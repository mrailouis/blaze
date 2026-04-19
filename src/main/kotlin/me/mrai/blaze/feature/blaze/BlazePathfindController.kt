package me.mrai.blaze.feature.blaze

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import me.mrai.blaze.chat.BlazeChat
import me.mrai.blaze.config.BlazeDataStore
import me.mrai.blaze.render.world.WorldBoxRenderer
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Blaze as BlazeMob
import net.minecraft.world.level.block.BaseFireBlock
import net.minecraft.world.level.block.CactusBlock
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.MagmaBlock
import net.minecraft.world.level.block.PowderSnowBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.SweetBerryBushBlock
import net.minecraft.world.level.block.WitherRoseBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object BlazePathfindController {
    private const val GOAL_DISTANCE = 2.35
    private const val GOAL_SEARCH_RADIUS = 6
    private const val GOAL_SEARCH_UP = 5
    private const val GOAL_SEARCH_DOWN = 10
    private const val START_SEARCH_RADIUS = 3
    private const val START_SEARCH_UP = 4
    private const val START_SEARCH_DOWN = 8
    private const val MAX_SEARCH_RADIUS = 320
    private const val MIN_SEARCH_RADIUS = 80
    private const val MIN_SEARCH_NODES = 16_000
    private const val MAX_SEARCH_NODES = 92_000
    private const val REPATH_INTERVAL_TICKS = 5
    private const val SAMPLE_SPACING = 0.18
    private const val CORNER_RADIUS = 0.92
    private const val PLAYER_OFF_ROUTE_DISTANCE = 3.8
    private const val TARGET_REPATH_DISTANCE_SQR = 1.15 * 1.15
    private const val BASE_LOOKAHEAD_DISTANCE = 2.1
    private const val BASE_JUMP_LOOKAHEAD_DISTANCE = 1.25
    private const val ROTATION_BASE_ALPHA = 0.075f
    private const val ROUTE_LINE_HALF_WIDTH = 0.065
    private const val ROUTE_SURFACE_Y_OFFSET = 0.018
    private const val ROUTE_OVERLAY_INSET = 0.07
    private const val ROUTE_OVERLAY_HEIGHT = 0.045
    private const val EDGE_DEBUG_SEARCH_RADIUS = 48
    private const val EDGE_DEBUG_NODE_LIMIT = 1_536
    private const val EDGE_DEBUG_SUPPORT_TOLERANCE = 0.24
    private const val EDGE_DROP_HORIZONTAL_REACH = 2
    private const val FLAT_ROUTE_POINT_SPACING = 1.55
    private const val TURN_IN_PLACE_YAW = 42.0f
    private const val TURN_RESUME_YAW = 24.0f
    private const val TURN_PREVIEW_MIN_ANGLE = 10.0f
    private const val HARD_STUCK_TIMEOUT_TICKS = 52
    private const val MAX_RECOVERY_ATTEMPTS = 6

    private val movementOffsets = listOf(
        NodeOffset(1, 0, 1.0),
        NodeOffset(-1, 0, 1.0),
        NodeOffset(0, 1, 1.0),
        NodeOffset(0, -1, 1.0),
        NodeOffset(1, 1, sqrt(2.0)),
        NodeOffset(1, -1, sqrt(2.0)),
        NodeOffset(-1, 1, sqrt(2.0)),
        NodeOffset(-1, -1, sqrt(2.0))
    )
    private val turnBlockStyle = WorldBoxRenderer.BoxStyle(
        fillRed = 0.12f,
        fillGreen = 0.82f,
        fillBlue = 1.0f,
        fillAlpha = 0.20f,
        outlineRed = 0.52f,
        outlineGreen = 0.93f,
        outlineBlue = 1.0f,
        outlineAlpha = 0.98f,
        outlineThickness = 0.03f
    )
    private val lineSegmentStyle = WorldBoxRenderer.BoxStyle(
        fillRed = 0.08f,
        fillGreen = 0.70f,
        fillBlue = 1.0f,
        fillAlpha = 0.16f,
        outlineRed = 0.36f,
        outlineGreen = 0.88f,
        outlineBlue = 1.0f,
        outlineAlpha = 0.90f,
        outlineThickness = 0.018f
    )
    private val edgeDebugStyle = WorldBoxRenderer.BoxStyle(
        fillRed = 0.96f,
        fillGreen = 0.18f,
        fillBlue = 0.92f,
        fillAlpha = 0.28f,
        outlineRed = 1.0f,
        outlineGreen = 0.62f,
        outlineBlue = 0.98f,
        outlineAlpha = 0.98f,
        outlineThickness = 0.036f
    )

    private var session: PathSession? = null
    private var edgeDebugBoxes: List<Pair<AABB, WorldBoxRenderer.BoxStyle>> = emptyList()
    private var pathDebugEnabled = false
    private var learning = LearningState()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    fun activeRouteBoxes(): List<Pair<AABB, WorldBoxRenderer.BoxStyle>> {
        return session?.route?.renderBoxes.orEmpty()
    }

    fun activeRouteQuads(): List<WorldBoxRenderer.SurfaceQuad> {
        return session?.route?.renderQuads.orEmpty()
    }

    fun activeEdgeDebugBoxes(): List<Pair<AABB, WorldBoxRenderer.BoxStyle>> {
        return edgeDebugBoxes
    }

    fun start(source: FabricClientCommandSource): Int {
        val client = source.client
        val level = client.level
        val player = client.player
        if (level == null || player == null) {
            BlazeChat.error(source, "You need to be in-game before pathfinding can start.")
            return 0
        }

        val target = findNearestBlaze(level, player)
        if (target == null) {
            BlazeChat.error(source, "No nearby Blaze could be found.")
            return 0
        }

        val debugTrace = PathDebugTrace()
        val planningStartedAt = System.nanoTime()
        val route = buildRoute(level, player, target.position(), target.blockPosition(), exactGoal = false, debug = debugTrace)
        val planningMs = (System.nanoTime() - planningStartedAt) / 1_000_000.0
        if (route == null) {
            emitPathDebug(source, debugTrace, force = true)
            BlazeChat.error(source, "Couldn't build a safe path to the nearest Blaze from here. [${debugTrace.terminalCode ?: "PF-000"} | ${"%.1f".format(planningMs)} ms]")
            return 0
        }
        emitPathDebug(source, debugTrace, force = false)

        releaseControls(client)
        val playerPosition = player.position()
        session = PathSession(
            targetId = target.id,
            staticTarget = null,
            exactGoal = false,
            arrivalDistance = GOAL_DISTANCE,
            targetLabel = "the nearest Blaze",
            route = route,
            routeCursor = 0,
            lastTargetPosition = target.position(),
            lastPlayerPosition = playerPosition,
            lastRemainingDistance = route.totalLength,
            stuckTicks = 0,
            recoveryAttempts = 0,
            repathTicks = REPATH_INTERVAL_TICKS,
            actionIndex = 0,
            actionPhase = ActionPhase.NONE,
            actionPhaseTicks = 0,
            rotationState = RotationControllerState()
        )
        BlazeChat.info(source, "Calculated route in ${"%.1f".format(planningMs)} ms.")
        BlazeChat.info(source, "Pathfinding to the nearest Blaze.")
        return 1
    }

    fun start(source: FabricClientCommandSource, destination: BlockPos): Int {
        val client = source.client
        val level = client.level
        val player = client.player
        if (level == null || player == null) {
            BlazeChat.error(source, "You need to be in-game before pathfinding can start.")
            return 0
        }

        val goalNode = resolveExplicitGoalNode(level, player, destination)
        val debugTrace = PathDebugTrace()
        val planningStartedAt = System.nanoTime()
        val route = buildRoute(level, player, nodeCenter(level, goalNode), goalNode, exactGoal = true, debug = debugTrace)
        val planningMs = (System.nanoTime() - planningStartedAt) / 1_000_000.0
        if (route == null) {
            emitPathDebug(source, debugTrace, force = true)
            BlazeChat.error(source, "Couldn't build a safe path to ${destination.x} ${destination.y} ${destination.z}. [${debugTrace.terminalCode ?: "PF-000"} | ${"%.1f".format(planningMs)} ms]")
            return 0
        }
        emitPathDebug(source, debugTrace, force = false)

        releaseControls(client)
        val playerPosition = player.position()
        session = PathSession(
            targetId = null,
            staticTarget = goalNode.immutable(),
            exactGoal = true,
            arrivalDistance = 0.85,
            targetLabel = "${destination.x} ${destination.y} ${destination.z}",
            route = route,
            routeCursor = 0,
            lastTargetPosition = nodeCenter(level, goalNode),
            lastPlayerPosition = playerPosition,
            lastRemainingDistance = route.totalLength,
            stuckTicks = 0,
            recoveryAttempts = 0,
            repathTicks = REPATH_INTERVAL_TICKS,
            actionIndex = 0,
            actionPhase = ActionPhase.NONE,
            actionPhaseTicks = 0,
            rotationState = RotationControllerState()
        )
        BlazeChat.info(source, "Calculated route in ${"%.1f".format(planningMs)} ms.")
        BlazeChat.info(source, "Pathfinding to ${destination.x} ${destination.y} ${destination.z}.")
        return 1
    }

    fun renderCurrentPlatformEdges(source: FabricClientCommandSource): Int {
        val client = source.client
        val level = client.level
        val player = client.player
        if (level == null || player == null) {
            BlazeChat.error(source, "You need to be in-game before edge debug can render.")
            return 0
        }

        val origin = findStandableNode(level, player, player.blockPosition(), 2, 3, 5)
        if (origin == null) {
            edgeDebugBoxes = emptyList()
            BlazeChat.error(source, "Couldn't resolve a standable platform under the player.")
            return 0
        }

        val edgeNodes = collectCurrentPlatformEdgeNodes(level, player, origin)
        edgeDebugBoxes = edgeNodes.map { routeMarkerBox(it, 0.02) to edgeDebugStyle }
        BlazeChat.info(source, "Rendered ${edgeNodes.size} current-level edge blocks.")
        return 1
    }

    fun setPathDebug(source: FabricClientCommandSource, enabled: Boolean): Int {
        pathDebugEnabled = enabled
        BlazeChat.info(source, if (enabled) "Pathfinding diagnostics enabled." else "Pathfinding diagnostics disabled.")
        return 1
    }

    private fun tick(client: Minecraft) {
        val activeSession = session ?: return
        val level = client.level
        val player = client.player
        if (level == null || player == null) {
            stop("Pathfinding stopped because the world is no longer available.")
            return
        }

        if (!BlazeDataStore.state.enabled) {
            stop("Pathfinding stopped because BLAZE is disabled.")
            return
        }

        if (client.screen != null || !client.isWindowActive) {
            releaseControls(client)
            return
        }

        if (!player.isAlive || player.isPassenger) {
            stop("Pathfinding stopped because the player can no longer move normally.")
            return
        }

        val target = resolveTarget(level, activeSession)
        if (target == null) {
            stop("Pathfinding stopped because the target Blaze disappeared.")
            return
        }

        val profile = detectMovementProfile(level, player, activeSession.lastPlayerPosition)
        val refreshedSession = maybeRepath(activeSession, level, player, target, profile)
        if (refreshedSession == null) {
            stop("Pathfinding stopped because the route is no longer reachable.")
            return
        }

        session = refreshedSession
        if (followRoute(client, level, player, target, refreshedSession, profile)) {
            stop("Arrived at ${refreshedSession.targetLabel}.")
        }
    }

    private fun maybeRepath(
        activeSession: PathSession,
        level: ClientLevel,
        player: LocalPlayer,
        target: TargetSnapshot,
        profile: MovementProfile
    ): PathSession? {
        val playerPosition = player.position()
        val projection = projectOntoRoute(activeSession.route.samples, playerPosition, activeSession.routeCursor)
        val airborne = !player.onGround() || abs(player.deltaMovement.y) > 0.08
        val movedDistance = playerPosition.distanceTo(activeSession.lastPlayerPosition)
        val progressDelta = activeSession.lastRemainingDistance - projection.remainingDistance
        val stuckTicks = if (!airborne && movedDistance < 0.045 && progressDelta < 0.035) activeSession.stuckTicks + 1 else 0
        val playerOffRoute = projection.distanceToRoute > max(PLAYER_OFF_ROUTE_DISTANCE, profile.recoveryRadius + 0.65)
        val targetPosition = target.anchor
        val targetMoved = targetPosition.distanceToSqr(activeSession.lastTargetPosition) >= TARGET_REPATH_DISTANCE_SQR
        val validationDue = activeSession.repathTicks <= 1
        val routeObstructed = validationDue && isRouteObstructed(level, player, activeSession.route.samples, projection.segmentIndex)
        val isStuck = stuckTicks >= learning.stuckThreshold
        val timedOutStuck = stuckTicks >= HARD_STUCK_TIMEOUT_TICKS

        val nextValidationTicks = if (validationDue) REPATH_INTERVAL_TICKS else max(1, activeSession.repathTicks - 1)
        if (airborne && !playerOffRoute && !targetMoved) {
            return activeSession.copy(
                routeCursor = projection.segmentIndex,
                lastPlayerPosition = playerPosition,
                lastRemainingDistance = projection.remainingDistance,
                stuckTicks = 0,
                repathTicks = nextValidationTicks
            )
        }

        if (!playerOffRoute && !targetMoved && !routeObstructed && !isStuck && !timedOutStuck) {
            learning = learning.observeProgress(progressDelta)
            return activeSession.copy(
                routeCursor = projection.segmentIndex,
                lastPlayerPosition = playerPosition,
                lastRemainingDistance = projection.remainingDistance,
                stuckTicks = stuckTicks,
                recoveryAttempts = if (progressDelta > 0.06) 0 else activeSession.recoveryAttempts,
                repathTicks = nextValidationTicks
            )
        }

        if (routeObstructed || isStuck || timedOutStuck) {
            learning = learning.observeFailure()
        }
        if (timedOutStuck && activeSession.recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            return null
        }
        val replanningStartedAt = System.nanoTime()
        val rebuiltRoute = buildRoute(level, player, target.anchor, target.blockPosition, activeSession.exactGoal) ?: return null
        BlazeChat.info("Recalculated route in ${"%.1f".format((System.nanoTime() - replanningStartedAt) / 1_000_000.0)} ms.")
        return activeSession.copy(
            route = rebuiltRoute,
            routeCursor = 0,
            lastTargetPosition = targetPosition,
            lastPlayerPosition = playerPosition,
            lastRemainingDistance = rebuiltRoute.totalLength,
            stuckTicks = 0,
            recoveryAttempts = if (routeObstructed || isStuck || timedOutStuck) activeSession.recoveryAttempts + 1 else activeSession.recoveryAttempts,
            repathTicks = REPATH_INTERVAL_TICKS,
            actionIndex = 0,
            actionPhase = ActionPhase.NONE,
            actionPhaseTicks = 0
        )
    }

    private fun followRoute(
        client: Minecraft,
        level: ClientLevel,
        player: LocalPlayer,
        target: TargetSnapshot,
        activeSession: PathSession,
        profile: MovementProfile
    ): Boolean {
        val playerPosition = player.position()
        val projection = projectOntoRoute(activeSession.route.samples, playerPosition, activeSession.routeCursor)
        val lookaheadPoint = pointAlongRoute(activeSession.route.samples, projection, profile.lookaheadDistance)
        val movementLookaheadPoint = pointAlongRoute(activeSession.route.samples, projection, max(0.95, profile.lookaheadDistance * 0.72))
        val jumpPoint = pointAlongRoute(activeSession.route.samples, projection, profile.jumpLookaheadDistance)
        val actionState = resolveActionState(activeSession, projection, playerPosition, player.onGround())
        val nextAction = actionState.action
        val committedVerticalAction = actionState.phase == ActionPhase.COMMIT ||
            actionState.phase == ActionPhase.TRAVERSE ||
            actionState.phase == ActionPhase.RECOVERY
        val turnPreview = if (committedVerticalAction) {
            null
        } else {
            previewUpcomingTurn(
                activeSession.route.samples,
                projection,
                max(2.35, profile.lookaheadDistance * 1.18)
            )
        }
        val turnSetupStrength = turnPreview?.let { turnSetupStrength(it, profile) } ?: 0.0f
        val turnSetupActive = turnSetupStrength >= 0.08f
        val correctionStrength = (projection.distanceToRoute / profile.recoveryRadius).coerceIn(0.0, 1.0)
        val actionLookTarget = when (nextAction?.type) {
            RouteActionType.JUMP -> nextAction.aimPoint.add(nextAction.exitTangent.scale(0.28))
            RouteActionType.DROP,
            RouteActionType.STEP_DOWN -> nextAction.landingPoint.add(nextAction.exitTangent.scale(0.22))
            else -> null
        }
        val forwardBlend = (0.97 - correctionStrength * 0.62).toFloat().coerceIn(0.28f, 0.97f)
        val magneticTarget = projection.point.lerp(lookaheadPoint, forwardBlend.toDouble())
        val baseRouteTravelTarget = projection.point.lerp(movementLookaheadPoint, 0.82)
        val turnApproachPoint = turnPreview?.turnPoint?.subtract(
            turnPreview.entryTangent.scale((0.18f + turnSetupStrength * 0.38f).coerceIn(0.18f, 0.56f).toDouble())
        ) ?: baseRouteTravelTarget
        val turnExitLookPoint = turnPreview?.turnPoint?.lerp(
            turnPreview.exitPoint,
            (0.22f + turnSetupStrength * 0.54f).coerceIn(0.22f, 0.78f).toDouble()
        ) ?: magneticTarget
        val routeLookTarget = if (turnSetupActive) {
            magneticTarget.lerp(
                turnExitLookPoint,
                (0.18f + turnSetupStrength * 0.54f).coerceIn(0.18f, 0.72f).toDouble()
            )
        } else {
            magneticTarget
        }
        val routeTravelTarget = if (turnSetupActive) {
            baseRouteTravelTarget.lerp(
                turnApproachPoint,
                (0.12f + turnSetupStrength * 0.58f).coerceIn(0.12f, 0.64f).toDouble()
            )
        } else {
            baseRouteTravelTarget
        }
        val blendedTarget = when (actionState.phase) {
            ActionPhase.ALIGN -> routeLookTarget.lerp(nextAction?.executePoint?.add(nextAction.entryTangent.scale(0.32)) ?: routeLookTarget, 0.34)
            ActionPhase.APPROACH -> routeLookTarget.lerp(actionLookTarget ?: routeLookTarget, 0.44)
            ActionPhase.COMMIT -> (nextAction?.aimPoint ?: routeLookTarget).lerp(actionLookTarget ?: routeLookTarget, 0.52)
            ActionPhase.TRAVERSE -> actionLookTarget ?: routeLookTarget
            ActionPhase.RECOVERY -> (nextAction?.landingPoint?.add(nextAction.exitTangent.scale(0.42)) ?: routeLookTarget).lerp(lookaheadPoint, 0.56)
            ActionPhase.NONE -> routeLookTarget
        }
        val movementTarget = when (actionState.phase) {
            ActionPhase.ALIGN -> routeTravelTarget.lerp(nextAction?.executePoint ?: routeTravelTarget, 0.24)
            ActionPhase.APPROACH -> routeTravelTarget.lerp(nextAction?.executePoint ?: routeTravelTarget, 0.46)
            ActionPhase.COMMIT -> when (nextAction?.type) {
                RouteActionType.JUMP -> nextAction.aimPoint
                RouteActionType.DROP,
                RouteActionType.STEP_DOWN -> nextAction.executePoint.lerp(nextAction.landingPoint, 0.42)
                else -> routeTravelTarget
            }
            ActionPhase.TRAVERSE -> nextAction?.landingPoint?.lerp(lookaheadPoint, 0.28) ?: routeTravelTarget
            ActionPhase.RECOVERY -> (nextAction?.landingPoint?.add(nextAction.exitTangent.scale(0.56)) ?: routeTravelTarget).lerp(routeTravelTarget, 0.52)
            ActionPhase.NONE -> routeTravelTarget
        }
        val actionTarget = blendedTarget.add(0.0, 0.82, 0.0)
        val locomotionAimTarget = movementTarget.add(
            0.0,
            (1.02 + min(0.24, profile.horizontalSpeed * 1.2)).coerceIn(1.02, 1.26),
            0.0
        )
        val locomotionLookBlend = when {
            actionState.phase == ActionPhase.COMMIT || actionState.phase == ActionPhase.TRAVERSE -> 0.20
            nextAction?.type == RouteActionType.JUMP -> 0.34
            nextAction?.type == RouteActionType.DROP || nextAction?.type == RouteActionType.STEP_DOWN -> 0.42
            else -> (0.68 + min(0.18, profile.horizontalSpeed * 1.45) + turnSetupStrength * 0.12).coerceIn(0.68, 0.90)
        }
        val aimTarget = actionTarget
            .add(0.0, 1.0, 0.0)
            .lerp(locomotionAimTarget, locomotionLookBlend)
        val desiredYaw = desiredYawTo(playerPosition, movementTarget)
        val predictedYawError = abs(Mth.wrapDegrees(desiredYaw - player.yRot))
        val upcomingTurn = (turnPreview?.angle ?: upcomingTurnAngle(activeSession.route.samples, projection.segmentIndex)) >= 26.0f ||
            nextAction?.type == RouteActionType.JUMP ||
            nextAction?.type == RouteActionType.DROP ||
            nextAction?.type == RouteActionType.STEP_DOWN
        val turnUrgency = (
            ((predictedYawError - TURN_RESUME_YAW) / 70.0f).coerceIn(0.0f, 1.0f) +
                if (upcomingTurn) 0.28f else 0.0f +
                turnSetupStrength * 0.42f
            ).coerceIn(0.0f, 1.0f)

        val rotationState = applySmoothRotation(
            player = player,
            lookTarget = aimTarget,
            moveTarget = movementTarget,
            profile = profile,
            correctionStrength = correctionStrength.toFloat(),
            turnUrgency = turnUrgency,
            rotationState = activeSession.rotationState
        )

        val signedYawError = Mth.wrapDegrees(desiredYaw - player.yRot)
        val absYawError = abs(signedYawError)
        val turnPreviewAngle = turnPreview?.angle ?: 0.0f
        val turnPreviewDistance = turnPreview?.distance ?: Double.MAX_VALUE
        val turnHoldDistance = (
            0.52 +
                min(0.92, profile.horizontalSpeed * 4.6) +
                turnPreviewAngle.toDouble() / 120.0
            ).coerceIn(0.58, 1.42)
        val turnBrakeDistance = (
            turnHoldDistance +
                0.42 +
                turnPreviewAngle.toDouble() / 140.0
            ).coerceAtMost(max(1.35, profile.lookaheadDistance * 0.72))
        val turnSetupHold = !committedVerticalAction &&
            turnSetupActive &&
            turnPreviewDistance <= turnHoldDistance &&
            absYawError >= (10.0f + turnPreviewAngle * 0.18f).coerceIn(14.0f, 30.0f)
        val turnSetupSlow = !committedVerticalAction &&
            turnSetupActive &&
            (turnPreviewDistance <= turnBrakeDistance || absYawError >= TURN_RESUME_YAW - 4.0f)
        val turnInPlace = !committedVerticalAction &&
            (turnSetupHold || absYawError >= TURN_IN_PLACE_YAW || (upcomingTurn && absYawError >= TURN_RESUME_YAW))
        val forward = if (committedVerticalAction) {
            absYawError <= min(profile.forwardYawLimit + 18.0f, 64.0f)
        } else {
            val forwardYawLimit = if (turnSetupSlow) {
                (22.0f + turnPreviewAngle * 0.10f - turnSetupStrength * 4.0f).coerceIn(18.0f, 30.0f)
            } else {
                min(profile.forwardYawLimit, 46.0f)
            }
            !turnInPlace && absYawError <= forwardYawLimit
        }
        val strafeLeft = !turnInPlace &&
            !turnSetupSlow &&
            !committedVerticalAction &&
            signedYawError < -profile.strafeDeadband &&
            absYawError <= min(profile.strafeYawLimit, 44.0f)
        val strafeRight = !turnInPlace &&
            !turnSetupSlow &&
            !committedVerticalAction &&
            signedYawError > profile.strafeDeadband &&
            absYawError <= min(profile.strafeYawLimit, 44.0f)
        val sprint = !committedVerticalAction &&
            !turnInPlace &&
            !turnSetupSlow &&
            forward &&
            absYawError <= profile.sprintYawLimit &&
            projection.distanceToRoute <= profile.recoveryRadius * 0.72 &&
            profile.horizontalSpeed <= max(profile.targetCruiseSpeed * 1.08, nextAction?.preferredSpeed ?: 0.0)
        val jump = shouldJump(level, player, jumpPoint, projection.point, profile, activeSession.route.jumpLaunchNodes, nextAction, actionState.phase)

        setControls(client, forward = forward, jump = jump, sprint = sprint, left = strafeLeft, right = strafeRight)
        session = activeSession.copy(
            routeCursor = projection.segmentIndex,
            lastPlayerPosition = playerPosition,
            lastRemainingDistance = projection.remainingDistance,
            actionIndex = actionState.index,
            actionPhase = actionState.phase,
            actionPhaseTicks = actionState.phaseTicks,
            rotationState = rotationState
        )

        val atRouteEnd = projection.remainingDistance <= max(0.85, profile.lookaheadDistance * 0.45) &&
            playerPosition.distanceTo(activeSession.route.goalPoint) <= 1.15
        val nearTarget = playerPosition.distanceTo(target.anchor) <= activeSession.arrivalDistance
        if (atRouteEnd || nearTarget) {
            learning = learning.observeSuccess()
            return true
        }
        return false
    }

    fun renderFrame() {
        val activeSession = session ?: return
        val player = Minecraft.getInstance().player ?: return
        val updatedRotationState = applyRotationPresentation(player, activeSession.rotationState, System.nanoTime())
        if (updatedRotationState != activeSession.rotationState) {
            session = activeSession.copy(rotationState = updatedRotationState)
        }
    }

    private fun applySmoothRotation(
        player: LocalPlayer,
        lookTarget: Vec3,
        moveTarget: Vec3,
        profile: MovementProfile,
        correctionStrength: Float,
        turnUrgency: Float,
        rotationState: RotationControllerState
    ): RotationControllerState {
        val settings = pathfinderConfig()
        val smoothness = settings.rotationSmoothness.toFloat().coerceIn(0.5f, 2.5f)
        val speedMultiplier = settings.rotationSpeedMultiplier.toFloat().coerceIn(0.4f, 5.0f)
        val movementLookBias = settings.movementLookBias.toFloat().coerceIn(0.1f, 1.0f)
        val headFreedom = settings.headFreedomDegrees.toFloat().coerceIn(10.0f, 45.0f)
        val smoothingDivisor = sqrt(smoothness)
        val eyePosition = player.position().add(0.0, player.eyeHeight.toDouble(), 0.0)
        val baseSpeedFactor = (profile.horizontalSpeed / max(0.12, profile.targetCruiseSpeed)).coerceIn(0.0, 1.6).toFloat()
        val effectiveSpeedMultiplier = speedMultiplier * (
            1.24f +
                min(1.0f, baseSpeedFactor) * 0.20f +
                turnUrgency * 0.28f
            )
        val smoothedLookTarget = rotationState.smoothedLookTarget?.lerp(
            lookTarget,
            ((0.14 + turnUrgency * 0.10 + correctionStrength * 0.05) / smoothingDivisor).coerceIn(0.09, 0.30)
        ) ?: lookTarget
        val smoothedMoveTarget = rotationState.smoothedMoveTarget?.lerp(
            moveTarget,
            ((0.16 + profile.horizontalSpeed * 0.24 + correctionStrength * 0.04) / smoothingDivisor).coerceIn(0.10, 0.32)
        ) ?: moveTarget
        val lookDelta = smoothedLookTarget.subtract(eyePosition)
        val horizontal = max(0.0001, sqrt(lookDelta.x * lookDelta.x + lookDelta.z * lookDelta.z))
        val viewYaw = desiredYawTo(player.position(), smoothedLookTarget)
        val moveYaw = desiredYawTo(player.position(), smoothedMoveTarget)
        val speedFactor = baseSpeedFactor
        val moveFacingBlend = (
            0.44f +
                turnUrgency * 0.14f +
                min(1.0f, speedFactor) * 0.24f +
                movementLookBias * 0.12f
            ).coerceIn(0.46f, 0.92f)
        val desiredYaw = lerpAngle(viewYaw, moveYaw, moveFacingBlend)
        val desiredPitch = Mth.clamp((-Math.toDegrees(atan2(lookDelta.y, horizontal))).toFloat(), -58.0f, 58.0f)
        val filteredViewYaw = lerpAngle(
            rotationState.filteredViewYaw ?: player.yRot,
            viewYaw,
            ((0.14f + turnUrgency * 0.08f + min(1.0f, speedFactor) * 0.08f) / smoothingDivisor).coerceIn(0.08f, 0.26f)
        )
        val filteredMoveYaw = lerpAngle(
            rotationState.filteredMoveYaw ?: player.yRot,
            moveYaw,
            ((0.18f + min(1.0f, speedFactor) * 0.12f) / smoothingDivisor).coerceIn(0.10f, 0.30f)
        )
        val rawYawTarget = lerpAngle(filteredViewYaw, filteredMoveYaw, moveFacingBlend)
        val filteredYawTarget = lerpAngle(
            rotationState.filteredYawTarget ?: player.yRot,
            rawYawTarget,
            ((0.12f + turnUrgency * 0.08f + min(1.0f, speedFactor) * 0.06f) / smoothingDivisor).coerceIn(0.07f, 0.22f)
        )
        val filteredPitchTarget = Mth.lerp(
            ((0.12f + turnUrgency * 0.07f + min(1.0f, speedFactor) * 0.06f) / smoothingDivisor).coerceIn(0.07f, 0.20f),
            rotationState.filteredPitch ?: player.xRot,
            desiredPitch
        )
        val now = System.nanoTime()
        val rotationCurve = refreshRotationCurve(
            currentYaw = player.yRot,
            currentPitch = player.xRot,
            targetYaw = filteredYawTarget,
            targetPitch = filteredPitchTarget,
            existing = rotationState.rotationCurve,
            speedMultiplier = effectiveSpeedMultiplier,
            smoothness = smoothness,
            turnUrgency = turnUrgency
        )
        val bodyYawTarget = lerpAngle(filteredMoveYaw, filteredYawTarget, (0.34f + min(1.0f, speedFactor) * 0.10f).coerceIn(0.34f, 0.52f))
        val headYawTarget = lerpAngle(filteredYawTarget, filteredMoveYaw, (0.62f + movementLookBias * 0.18f).coerceIn(0.62f, 0.86f))
        val plannedState = rotationState.copy(
            smoothedLookTarget = smoothedLookTarget,
            smoothedMoveTarget = smoothedMoveTarget,
            filteredViewYaw = filteredViewYaw,
            filteredMoveYaw = filteredMoveYaw,
            filteredYawTarget = filteredYawTarget,
            filteredPitch = filteredPitchTarget,
            rotationCurve = rotationCurve,
            bodyYawTarget = bodyYawTarget,
            headYawTarget = headYawTarget,
            headFreedom = headFreedom
        )
        return applyRotationPresentation(player, plannedState, now)
    }

    private fun bezierRotationStep(
        error: Float,
        minStep: Float,
        maxStep: Float,
        fullError: Float
    ): Float {
        val progress = (abs(error) / max(1.0f, fullError)).coerceIn(0.0f, 1.0f)
        return Mth.lerp(cubicBezierBlend(progress), minStep, maxStep)
    }

    private fun rotateToward(current: Float, target: Float, maxStep: Float): Float {
        return current + Mth.wrapDegrees(target - current).coerceIn(-maxStep, maxStep)
    }

    private fun rotateTowardLinear(current: Float, target: Float, maxStep: Float): Float {
        return current + (target - current).coerceIn(-maxStep, maxStep)
    }

    private fun refreshRotationCurve(
        currentYaw: Float,
        currentPitch: Float,
        targetYaw: Float,
        targetPitch: Float,
        existing: RotationCurve?,
        speedMultiplier: Float,
        smoothness: Float,
        turnUrgency: Float
    ): RotationCurve {
        val normalizedTargetYaw = currentYaw + Mth.wrapDegrees(targetYaw - currentYaw)
        val targetShiftYaw = existing?.let { abs(Mth.wrapDegrees(normalizedTargetYaw - it.endYaw)) } ?: Float.MAX_VALUE
        val targetShiftPitch = existing?.let { abs(targetPitch - it.endPitch) } ?: Float.MAX_VALUE
        val progress = existing?.progressAt(System.nanoTime()) ?: 1.0f
        if (existing != null && progress < 0.985f && targetShiftYaw <= 2.5f && targetShiftPitch <= 1.4f) {
            return existing
        }

        val deltaYaw = Mth.wrapDegrees(normalizedTargetYaw - currentYaw)
        val deltaPitch = targetPitch - currentPitch
        val distance = sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch).coerceAtLeast(0.001f)
        val durationMs = (
            (18.0f + distance * 2.05f + turnUrgency * 14.0f) *
                (0.76f + smoothness * 0.28f) /
                max(0.42f, speedMultiplier * 1.08f)
            ).coerceIn(12.0f, 96.0f)
        val midYaw = currentYaw + deltaYaw * 0.5f
        val midPitch = currentPitch + deltaPitch * 0.5f
        val length = distance.coerceAtLeast(1.0f)
        val perpYaw = -deltaPitch / length
        val perpPitch = deltaYaw / length
        val arcMultiplier = distance * (0.055f + turnUrgency * 0.035f).coerceIn(0.04f, 0.11f)
        return RotationCurve(
            startYaw = currentYaw,
            startPitch = currentPitch,
            controlYaw = midYaw + perpYaw * arcMultiplier,
            controlPitch = midPitch + perpPitch * arcMultiplier,
            endYaw = currentYaw + deltaYaw,
            endPitch = Mth.clamp(targetPitch, -58.0f, 58.0f),
            startNanos = System.nanoTime(),
            durationNanos = (durationMs * 1_000_000.0f).toLong().coerceAtLeast(1L)
        )
    }

    private fun applyRotationPresentation(
        player: LocalPlayer,
        rotationState: RotationControllerState,
        now: Long
    ): RotationControllerState {
        val curve = rotationState.rotationCurve ?: return rotationState
        val progress = curve.progressAt(now)
        val eased = easeInOutCubic(progress)
        val u = 1.0f - eased
        val nextYaw = normalizeYaw(
            u * u * curve.startYaw +
                2.0f * u * eased * curve.controlYaw +
                eased * eased * curve.endYaw
        )
        val nextPitch = Mth.clamp(
            u * u * curve.startPitch +
                2.0f * u * eased * curve.controlPitch +
                eased * eased * curve.endPitch,
            -58.0f,
            58.0f
        )
        val bodyTarget = rotationState.bodyYawTarget ?: nextYaw
        val headTarget = rotationState.headYawTarget ?: nextYaw
        val nextBodyYaw = lerpAngle(player.yBodyRot, bodyTarget, 0.34f)
        val unclampedHeadYaw = lerpAngle(player.yHeadRot, headTarget, 0.42f)
        val headFreedom = rotationState.headFreedom ?: 32.0f
        val headBodyDelta = Mth.wrapDegrees(unclampedHeadYaw - nextBodyYaw).coerceIn(-headFreedom, headFreedom)
        val nextHeadYaw = nextBodyYaw + headBodyDelta

        player.setYRot(nextYaw)
        player.yRot = nextYaw
        player.yRotO = nextYaw
        player.setXRot(nextPitch)
        player.xRot = nextPitch
        player.xRotO = nextPitch
        player.yBodyRot = nextBodyYaw
        player.yBodyRotO = nextBodyYaw
        player.setYHeadRot(nextHeadYaw)
        player.yHeadRot = nextHeadYaw
        player.yHeadRotO = nextHeadYaw

        return if (progress >= 0.999f) {
            rotationState.copy(rotationCurve = null)
        } else {
            rotationState
        }
    }

    private fun easeInOutCubic(progress: Float): Float {
        val x = progress.coerceIn(0.0f, 1.0f)
        return if (x < 0.5f) {
            4.0f * x * x * x
        } else {
            1.0f - ((-2.0f * x + 2.0f).let { it * it * it } / 2.0f)
        }
    }

    private fun normalizeYaw(yaw: Float): Float {
        var normalized = yaw % 360.0f
        if (normalized > 180.0f) normalized -= 360.0f
        if (normalized < -180.0f) normalized += 360.0f
        return normalized
    }

    private fun cubicBezierBlend(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        val inv = 1f - t
        val p0 = 0f
        val p1 = 0.10f
        val p2 = 0.90f
        val p3 = 1f
        return inv * inv * inv * p0 +
            3f * inv * inv * t * p1 +
            3f * inv * t * t * p2 +
            t * t * t * p3
    }

    private fun lerpAngle(start: Float, end: Float, alpha: Float): Float {
        return start + Mth.wrapDegrees(end - start) * alpha
    }

    private fun desiredYawTo(from: Vec3, target: Vec3): Float {
        return Mth.wrapDegrees((Math.toDegrees(atan2(target.z - from.z, target.x - from.x)) - 90.0).toFloat())
    }

    private fun setControls(
        client: Minecraft,
        forward: Boolean,
        jump: Boolean,
        sprint: Boolean,
        left: Boolean,
        right: Boolean
    ) {
        client.options.keyUp.setDown(forward)
        client.options.keyDown.setDown(false)
        client.options.keyLeft.setDown(left)
        client.options.keyRight.setDown(right)
        client.options.keyJump.setDown(jump)
        client.options.keySprint.setDown(sprint)
    }

    private fun releaseControls(client: Minecraft) {
        setControls(client, forward = false, jump = false, sprint = false, left = false, right = false)
    }

    private fun stop(message: String) {
        releaseControls(Minecraft.getInstance())
        session = null
        BlazeChat.info(message)
    }

    private fun resolveTarget(level: ClientLevel, session: PathSession): TargetSnapshot? {
        val targetId = session.targetId
        if (targetId != null) {
            val blaze = level.getEntity(targetId) as? BlazeMob ?: return null
            if (blaze.isRemoved || !blaze.isAlive) {
                return null
            }
            return TargetSnapshot(blaze.position(), blaze.blockPosition())
        }

        val staticTarget = session.staticTarget ?: return null
        return TargetSnapshot(nodeCenter(level, staticTarget), staticTarget)
    }

    private fun resolveExplicitGoalNode(level: ClientLevel, player: LocalPlayer, requested: BlockPos): BlockPos {
        val directCandidates = listOf(
            requested,
            requested.above(),
            requested.below(),
            requested.above(2),
            requested.below(2)
        )
        directCandidates.firstOrNull { isStandable(level, player, it) }?.let { return it }
        return findStandableNode(level, player, requested, 2, 4, 5)
            ?: findStandableNode(level, player, requested.above(), 2, 4, 5)
            ?: requested
    }

    private fun detectMovementProfile(level: ClientLevel, player: LocalPlayer, previousPosition: Vec3): MovementProfile {
        val currentPosition = player.position()
        val horizontalSpeed = max(
            horizontalDistance(currentPosition.subtract(previousPosition)),
            horizontalDistance(Vec3(player.deltaMovement.x, 0.0, player.deltaMovement.z))
        )
        val floorPos = BlockPos.containing(player.x, player.y - 0.35, player.z)
        val floorState = level.getBlockState(floorPos)
        val block = floorState.block
        val speedFactor = block.getSpeedFactor().toDouble()
        val jumpFactor = block.getJumpFactor().toDouble()
        val friction = block.getFriction().toDouble()
        val speedLevel = effectLevel(player, MobEffects.SPEED) - effectLevel(player, MobEffects.SLOWNESS)
        val jumpLevel = effectLevel(player, MobEffects.JUMP_BOOST)
        val attributeMoveSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED).coerceAtLeast(0.02)
        val movementSpeedModifier = (attributeMoveSpeed / 0.1).coerceIn(0.35, 14.0)
        val sprintMultiplier = if (player.isSprinting) 1.3 else 1.0
        val documentedJumpHeight = documentedJumpHeight(jumpLevel)
        val expectedGroundSpeed = (
            0.18 *
                movementSpeedModifier *
                sprintMultiplier *
                speedFactor /
                max(0.55, friction)
            ).coerceIn(0.14, 1.45)
        val effectiveGroundSpeed = max(horizontalSpeed, expectedGroundSpeed)
        val jumpImpulse = (
            (0.42 + max(0, jumpLevel) * 0.10) *
                jumpFactor *
                (1.0 + (movementSpeedModifier - 1.0).coerceAtLeast(0.0) * 0.012)
            ).coerceIn(0.42, 1.28)
        val lookaheadDistance = (
            BASE_LOOKAHEAD_DISTANCE +
                effectiveGroundSpeed * 14.0 +
                movementSpeedModifier * 0.30 +
                max(0, jumpLevel) * 0.18 +
                learning.lookaheadBonus +
                if (friction < 0.74) 0.55 else 0.0 +
                if (speedFactor > 1.02) 0.35 else 0.0
            ).coerceIn(1.9, 12.0)
        val jumpLookaheadDistance = (
            BASE_JUMP_LOOKAHEAD_DISTANCE +
                effectiveGroundSpeed * 8.8 +
                movementSpeedModifier * 0.24 +
                max(0, jumpLevel) * 0.18
            ).coerceIn(1.0, 7.0)
        val recoveryRadius = (
            0.92 +
                effectiveGroundSpeed * 10.0 +
                movementSpeedModifier * 0.18 +
                max(0, jumpLevel) * 0.18 +
                if (friction < 0.74) 0.65 else 0.0
            ).coerceIn(1.0, 8.2)
        val targetCruiseSpeed = effectiveGroundSpeed.coerceIn(0.18, 1.35)
        val forwardYawLimit = (98.0 - effectiveGroundSpeed * 72.0).toFloat().coerceIn(34.0f, 98.0f)
        val strafeYawLimit = (76.0 - effectiveGroundSpeed * 28.0).toFloat().coerceIn(24.0f, 76.0f)
        val sprintYawLimit = (22.0 - effectiveGroundSpeed * 8.0).toFloat().coerceIn(8.0f, 22.0f)
        val jumpAssistHeight = (
            documentedJumpHeight * jumpFactor +
                (jumpFactor - 1.0) * 0.12 +
                (movementSpeedModifier - 1.0).coerceAtLeast(0.0) * 0.05
            ).coerceIn(0.48, 5.2)
        val jumpLaunchLeadDistance = (
            (effectiveGroundSpeed - 0.18).coerceAtLeast(0.0) * 3.4 +
                (jumpAssistHeight - 1.25).coerceAtLeast(0.0) * 0.22
            ).coerceIn(0.0, 2.1)
        val jumpRunupDistance = (
            0.9 +
                effectiveGroundSpeed * 2.6 +
                (jumpAssistHeight - 1.25).coerceAtLeast(0.0) * 0.34
            ).coerceIn(0.9, 4.2)

        return MovementProfile(
            horizontalSpeed = horizontalSpeed,
            speedLevel = speedLevel,
            jumpLevel = jumpLevel,
            movementSpeedModifier = movementSpeedModifier,
            lookaheadDistance = lookaheadDistance,
            jumpLookaheadDistance = jumpLookaheadDistance,
            recoveryRadius = recoveryRadius,
            targetCruiseSpeed = targetCruiseSpeed,
            forwardYawLimit = forwardYawLimit,
            strafeYawLimit = strafeYawLimit,
            sprintYawLimit = sprintYawLimit,
            strafeDeadband = 7.5f,
            jumpAssistHeight = jumpAssistHeight,
            jumpImpulse = jumpImpulse,
            jumpLaunchLeadDistance = jumpLaunchLeadDistance,
            jumpRunupDistance = jumpRunupDistance
        )
    }

    private fun effectLevel(player: LocalPlayer, effect: net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>): Int {
        return player.getEffect(effect)?.amplifier?.plus(1) ?: 0
    }

    private fun shouldJump(
        level: ClientLevel,
        player: LocalPlayer,
        jumpTarget: Vec3,
        routePoint: Vec3,
        profile: MovementProfile,
        jumpLaunchNodes: Set<BlockPos>,
        nextAction: RouteAction?,
        actionPhase: ActionPhase
    ): Boolean {
        if (!player.onGround()) {
            return false
        }

        if (nextAction?.type == RouteActionType.JUMP) {
            val actionDistance = horizontalDistance(nextAction.executePoint.subtract(player.position()))
            val yawError = abs(Mth.wrapDegrees(desiredYawTo(player.position(), nextAction.aimPoint) - player.yRot))
            val launchDistance = nextAction.commitmentDistance + profile.jumpLaunchLeadDistance
            if ((actionPhase == ActionPhase.COMMIT || actionPhase == ActionPhase.TRAVERSE) && yawError <= 62.0f) {
                return true
            }
            if (profile.horizontalSpeed >= nextAction.minimumRequiredSpeed &&
                actionDistance <= launchDistance &&
                yawError <= 52.0f
            ) {
                return true
            }
        }

        if (nextAction?.type == RouteActionType.WALK || nextAction?.type == RouteActionType.DROP || nextAction?.type == RouteActionType.STEP_DOWN) {
            return false
        }

        if (shouldLaunchPlannedJump(level, player, jumpLaunchNodes, profile)) {
            return true
        }

        val heightDelta = jumpTarget.y - player.y
        if (heightDelta <= 0.42 || heightDelta > profile.jumpAssistHeight + 0.62) {
            return false
        }

        val frontPos = BlockPos.containing(routePoint.x, routePoint.y - 0.45, routePoint.z)
        val frontFloor = level.getBlockState(frontPos)
        if (isSmoothWalkSurface(level, frontPos, frontFloor)) {
            return false
        }
        val frontObstacle = !frontFloor.getCollisionShape(level, frontPos).isEmpty
        return frontObstacle ||
            hasUpcomingStep(level, player, routePoint, jumpTarget) ||
            horizontalDistance(jumpTarget.subtract(player.position())) < profile.jumpLookaheadDistance + 0.35 + profile.jumpLaunchLeadDistance
    }

    private fun hasUpcomingStep(level: ClientLevel, player: LocalPlayer, routePoint: Vec3, jumpTarget: Vec3): Boolean {
        val routeFloorPos = BlockPos.containing(routePoint.x, routePoint.y - 0.45, routePoint.z)
        if (isSmoothWalkSurface(level, routeFloorPos, level.getBlockState(routeFloorPos))) {
            return false
        }
        if (jumpTarget.y - player.y > 0.42) {
            return true
        }

        val frontFeetPos = BlockPos.containing(routePoint.x, player.y + 0.15, routePoint.z)
        val frontHeadPos = frontFeetPos.above()
        val feetBlocked = !level.getBlockState(frontFeetPos).getCollisionShape(level, frontFeetPos).isEmpty
        val headBlocked = !level.getBlockState(frontHeadPos).getCollisionShape(level, frontHeadPos).isEmpty
        return feetBlocked && !headBlocked
    }

    private fun shouldLaunchPlannedJump(level: ClientLevel, player: LocalPlayer, jumpLaunchNodes: Set<BlockPos>, profile: MovementProfile): Boolean {
        if (jumpLaunchNodes.isEmpty()) {
            return false
        }
        val position = player.position()
        return jumpLaunchNodes.any { node ->
            horizontalDistance(nodeCenter(level, node).subtract(position)) <= 0.92 + profile.jumpLaunchLeadDistance &&
                abs(node.y - player.blockPosition().y) <= 1
        }
    }

    private fun resolveActionState(
        activeSession: PathSession,
        projection: RouteProjection,
        playerPosition: Vec3,
        onGround: Boolean
    ): ActionExecutionState {
        val actions = activeSession.route.actions
        var index = activeSession.actionIndex.coerceAtLeast(0)
        while (index < actions.size) {
            val candidate = actions[index]
            if (candidate.type == RouteActionType.WALK || isRouteActionComplete(candidate, projection.point)) {
                index++
                continue
            }
            break
        }
        if (index >= actions.size) {
            return ActionExecutionState(null, actions.size, ActionPhase.NONE, 0)
        }

        val action = actions[index]
        val previousPhase = if (index == activeSession.actionIndex) activeSession.actionPhase else ActionPhase.NONE
        val progress = actionProgress(action, projection.point)
        val executeDistance = horizontalDistance(action.executePoint.subtract(playerPosition))
        val commitmentReached = executeDistance <= action.commitmentDistance || progress >= action.completionProgress * 0.45
        val completionReached = isRouteActionComplete(action, projection.point)
        var phase = when {
            completionReached && onGround -> ActionPhase.RECOVERY
            !onGround && previousPhase >= ActionPhase.COMMIT -> ActionPhase.TRAVERSE
            commitmentReached && previousPhase >= ActionPhase.APPROACH -> ActionPhase.COMMIT
            executeDistance <= action.commitmentDistance * 1.75 -> ActionPhase.APPROACH
            else -> ActionPhase.ALIGN
        }
        if (previousPhase > phase && previousPhase != ActionPhase.RECOVERY && !completionReached) {
            phase = previousPhase
        }

        var phaseTicks = if (index == activeSession.actionIndex && phase == previousPhase) activeSession.actionPhaseTicks + 1 else 0
        if (phase == ActionPhase.RECOVERY && phaseTicks >= 4) {
            index++
            while (index < actions.size && (actions[index].type == RouteActionType.WALK || isRouteActionComplete(actions[index], projection.point))) {
                index++
            }
            return if (index >= actions.size) {
                ActionExecutionState(null, actions.size, ActionPhase.NONE, 0)
            } else {
                ActionExecutionState(actions[index], index, ActionPhase.ALIGN, 0)
            }
        }

        return ActionExecutionState(action, index, phase, phaseTicks)
    }

    private fun isRouteActionComplete(action: RouteAction, routePoint: Vec3): Boolean {
        return actionProgress(action, routePoint) >= action.completionProgress ||
            routePoint.distanceTo(action.landingPoint) <= action.lateralTolerance
    }

    private fun actionProgress(action: RouteAction, routePoint: Vec3): Double {
        val pathVector = action.landingPoint.subtract(action.executePoint)
        val pathLengthSqr = pathVector.lengthSqr()
        if (pathLengthSqr <= 1.0e-5) {
            return if (routePoint.distanceTo(action.landingPoint) <= action.lateralTolerance) 1.0 else 0.0
        }
        return (routePoint.subtract(action.executePoint).dot(pathVector) / pathLengthSqr).coerceIn(-0.2, 1.4)
    }

    private fun findNearestBlaze(level: ClientLevel, player: LocalPlayer): BlazeMob? {
        return level.entitiesForRendering()
            .filterIsInstance<BlazeMob>()
            .filter { !it.isRemoved && it.isAlive }
            .minByOrNull { it.distanceToSqr(player) }
    }

    private fun buildRoute(
        level: ClientLevel,
        player: LocalPlayer,
        targetAnchor: Vec3,
        targetBlock: BlockPos,
        exactGoal: Boolean,
        debug: PathDebugTrace? = null
    ): RoutePlan? {
        val capabilities = detectTraversalCapabilities(detectMovementProfile(level, player, player.position()))
        val start = findStandableNode(level, player, player.blockPosition(), START_SEARCH_RADIUS, START_SEARCH_UP, START_SEARCH_DOWN)
            ?: return debug.fail("PF-101", "No standable start node near player at ${player.blockPosition().x} ${player.blockPosition().y} ${player.blockPosition().z}.")
        val searchConfig = searchConfig(start, targetBlock)
        debug?.log("PF-102", "Start=$start Target=$targetBlock Radius=${searchConfig.horizontalRadius} MaxNodes=${searchConfig.maxNodes}")
        val snapshot = captureTerrainSnapshot(level, player, start, targetBlock, searchConfig, capabilities)
        val goalNodes = findGoalNodes(level, player, snapshot, start, targetBlock, targetAnchor, capabilities, exactGoal)
        if (goalNodes.isEmpty()) {
            return debug.fail("PF-103", "No reachable goal candidates near target $targetBlock.")
        }
        debug?.log("PF-104", "Goal candidates=${goalNodes.size} Closest=${goalNodes.firstOrNull()}")

        val strictPath = findPath(level, player, snapshot, start, goalNodes, searchConfig, relaxed = false)
            ?.also { debug?.log("PF-201", "Strict search succeeded with ${it.size} nodes.") }
        if (strictPath == null) {
            debug?.log("PF-210", "Strict search failed.")
        }
        val directPreferred = strictPath ?: buildDirectFallbackPath(level, player, snapshot, start, goalNodes, searchConfig.horizontalRadius, debug)
        if (strictPath == null && directPreferred == null) {
            debug?.log("PF-221", "Direct planner failed.")
        }
        val relaxedPath = if (strictPath == null && directPreferred == null) {
            findPath(level, player, snapshot, start, goalNodes, searchConfig, relaxed = true)
        } else {
            null
        }
        if (strictPath == null && directPreferred == null && relaxedPath == null) {
            debug?.log("PF-230", "Relaxed search failed.")
        }
        val descendingFallback = if (strictPath == null && directPreferred == null && relaxedPath == null && targetBlock.y < start.y) {
            buildDescendingFallbackPath(level, player, snapshot, start, goalNodes, targetAnchor, searchConfig.horizontalRadius, debug)
        } else {
            null
        }
        if (strictPath == null && directPreferred == null && relaxedPath == null && targetBlock.y < start.y && descendingFallback == null) {
            debug?.log("PF-241", "Descending fallback failed.")
        }
        val pathNodes: List<BlockPos> = strictPath
            ?: directPreferred?.also { debug?.log("PF-202", "Direct planner succeeded with ${it.size} nodes.") }
            ?: relaxedPath
                ?.also { debug?.log("PF-203", "Relaxed search succeeded with ${it.size} nodes.") }
            ?: descendingFallback?.also { debug?.log("PF-204", "Descending fallback succeeded with ${it.size} nodes.") }
            ?: return debug.fail("PF-299", "All route construction stages failed (strict/direct/relaxed/descent).")
        val routeActions = buildRouteActions(level, pathNodes)
        val jumpLaunchNodes = pathNodes.zipWithNext()
            .filter { (from, to) -> to.y > from.y }
            .mapTo(linkedSetOf()) { it.first }
        val simplifiedNodes = simplifyPath(level, player, pathNodes)
        val renderNodes = extractMajorRouteNodes(level, simplifiedNodes)
        val followNodes = buildFollowRouteNodes(level, simplifiedNodes)
        val smoothedSamples = buildSmoothedSamples(level, player, followNodes.map { nodeCenter(level, it) })
        if (smoothedSamples.isEmpty()) {
            return debug.fail("PF-301", "Route smoothing produced no samples from ${followNodes.size} follow nodes.")
        }
        debug?.log("PF-302", "RenderNodes=${renderNodes.size} FollowNodes=${followNodes.size} Actions=${routeActions.size} JumpLaunches=${jumpLaunchNodes.size} Samples=${smoothedSamples.size}")

        return RoutePlan(
            pathNodes = renderNodes,
            samples = smoothedSamples,
            goalPoint = nodeCenter(level, followNodes.last()),
            renderBoxes = buildRenderBoxes(level, renderNodes),
            renderQuads = buildRenderQuads(level, renderNodes),
            actions = routeActions,
            jumpLaunchNodes = jumpLaunchNodes,
            totalLength = routeLength(smoothedSamples)
        )
    }

    private fun searchConfig(start: BlockPos, target: BlockPos): SearchConfig {
        val horizontalDistance = sqrt(
            ((target.x - start.x) * (target.x - start.x) + (target.z - start.z) * (target.z - start.z)).toDouble()
        )
        val verticalDistance = abs(target.y - start.y)
        val radius = (horizontalDistance.roundToInt() + 40 + verticalDistance * 2 + learning.searchRadiusBonus).coerceIn(MIN_SEARCH_RADIUS, MAX_SEARCH_RADIUS)
        val maxNodes = (radius * radius * 1.42 + verticalDistance * 520 + learning.searchRadiusBonus * 360).roundToInt().coerceIn(MIN_SEARCH_NODES, MAX_SEARCH_NODES)
        return SearchConfig(radius, maxNodes)
    }

    private fun findGoalNodes(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        start: BlockPos,
        targetPos: BlockPos,
        targetAnchor: Vec3,
        capabilities: TraversalCapabilities,
        exactGoal: Boolean
    ): Set<BlockPos> {
        val candidates = linkedMapOf<BlockPos, Double>()
        val startCenter = nodeCenter(level, snapshot, start)
        val targetBelowStart = targetAnchor.y < startCenter.y - 0.35
        val verticalGap = abs(startCenter.y - targetAnchor.y)
        val dynamicRadius = GOAL_SEARCH_RADIUS + capabilities.maxJumpUp + learning.verticalSearchBonus
        val dynamicUp = GOAL_SEARCH_UP + capabilities.maxJumpUp + learning.verticalSearchBonus
        val dynamicDown = GOAL_SEARCH_DOWN + capabilities.maxSafeDrop / 2 + if (targetBelowStart) min(18, ceil(verticalGap).toInt()) else 0
        val goalHorizontalLimit = if (exactGoal) 1.65 else GOAL_DISTANCE + 5.4
        val goalVerticalLimit = (if (exactGoal) 6.5 else 8.5) + if (targetBelowStart) min(10.0, verticalGap * 0.8) else 0.0

        fun addCandidate(candidate: BlockPos) {
            if (!isStandable(level, player, snapshot, candidate)) {
                return
            }
            val candidateCenter = nodeCenter(level, snapshot, candidate)
            val horizontal = horizontalDistance(candidateCenter.subtract(targetAnchor))
            val vertical = abs(candidateCenter.y - targetAnchor.y)
            if (horizontal > goalHorizontalLimit || vertical > goalVerticalLimit) {
                return
            }
            val startVertical = abs(candidate.y - start.y).toDouble()
            val startHorizontal = horizontalDistance(candidateCenter.subtract(startCenter))
            val belowTargetPenalty = max(0.0, targetAnchor.y - candidateCenter.y) * 0.14
            val descendingContinuityBias = if (targetBelowStart && candidateCenter.y <= startCenter.y + 0.5) {
                -min(0.58, max(0.0, candidateCenter.y - targetAnchor.y) * 0.045)
            } else {
                0.0
            }
            val score = horizontal +
                vertical * if (targetBelowStart) 0.30 else 0.42 +
                startVertical * if (targetBelowStart) 0.24 else 0.33 +
                startHorizontal * 0.035 +
                belowTargetPenalty +
                descendingContinuityBias
            val existing = candidates[candidate]
            if (existing == null || score < existing) {
                candidates[candidate] = score
            }
        }

        for (xOffset in -dynamicRadius..dynamicRadius) {
            for (zOffset in -dynamicRadius..dynamicRadius) {
                for (yOffset in dynamicUp downTo -dynamicDown) {
                    addCandidate(targetPos.offset(xOffset, yOffset, zOffset))
                }
            }
        }

        addCandidate(targetPos)
        addCandidate(BlockPos(targetPos.x, start.y, targetPos.z))
        findStandableNode(level, player, BlockPos(targetPos.x, start.y, targetPos.z), dynamicRadius, dynamicUp, dynamicDown)?.let(::addCandidate)
        findStandableNode(level, player, targetPos, dynamicRadius, dynamicUp, dynamicDown)?.let(::addCandidate)

        return candidates
            .entries
            .sortedBy { it.value }
            .take(if (exactGoal) 48 else 72)
            .mapTo(linkedSetOf()) { it.key }
    }

    private fun findPath(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        start: BlockPos,
        goalNodes: Set<BlockPos>,
        searchConfig: SearchConfig,
        relaxed: Boolean
    ): List<BlockPos>? {
        val goalCenters = goalNodes.map { nodeCenter(level, snapshot, it) }
        val openSet = java.util.PriorityQueue<PathStep>(compareBy<PathStep> { it.fScore }.thenBy { it.hScore })
        val cameFrom = HashMap<BlockPos, BlockPos>()
        val gScore = HashMap<BlockPos, Double>()
        val closedSet = HashSet<BlockPos>()

        val initialH = heuristic(level, snapshot, start, goalCenters)
        openSet.add(PathStep(start, initialH, initialH))
        gScore[start] = 0.0

        var explored = 0
        while (openSet.isNotEmpty() && explored < searchConfig.maxNodes) {
            val current = openSet.poll().position
            if (!closedSet.add(current)) {
                continue
            }
            explored++

            if (current in goalNodes) {
                return reconstructPath(cameFrom, current)
            }

            for ((neighbor, moveCost) in neighbors(level, player, snapshot, current, start, searchConfig.horizontalRadius, relaxed)) {
                if (neighbor in closedSet) {
                    continue
                }

                val tentativeG = gScore.getValue(current) +
                    moveCost +
                    terrainPenalty(level, neighbor) +
                    opennessPenalty(level, neighbor) +
                    supportContinuityPenalty(level, snapshot, current, neighbor) +
                    headingFlowPenalty(level, snapshot, cameFrom[current], current, neighbor) +
                    goalProgressBias(level, snapshot, current, neighbor, goalCenters)
                if (tentativeG >= gScore.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    continue
                }

                cameFrom[neighbor] = current
                gScore[neighbor] = tentativeG
                val h = heuristic(level, snapshot, neighbor, goalCenters)
                openSet.add(PathStep(neighbor, tentativeG + h, h))
            }
        }

        return null
    }

    private fun neighbors(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        current: BlockPos,
        start: BlockPos,
        searchRadius: Int,
        relaxed: Boolean
    ): List<Pair<BlockPos, Double>> {
        val capabilities = detectTraversalCapabilities(detectMovementProfile(level, player, nodeCenter(level, snapshot, current)))
        val neighbors = LinkedHashMap<BlockPos, Double>(movementOffsets.size + 8)
        for (offset in movementOffsets) {
            if (offset.dx != 0 && offset.dz != 0 && !canTraverseDiagonal(level, player, snapshot, current, offset, capabilities)) {
                continue
            }

            val base = current.offset(offset.dx, 0, offset.dz)
            val seen = linkedSetOf<BlockPos>()
            for (yOffset in candidateYOffsets(capabilities)) {
                val candidate = base.offset(0, yOffset, 0)
                if (!seen.add(candidate)) {
                    continue
                }
                if (abs(candidate.x - start.x) > searchRadius || abs(candidate.z - start.z) > searchRadius) {
                    continue
                }
                if (candidate.y - current.y > capabilities.maxJumpUp || current.y - candidate.y > capabilities.maxSafeDrop) {
                    continue
                }
                if (!isStandable(level, player, snapshot, candidate)) {
                    continue
                }
                val transition = resolveTransition(level, player, snapshot, current, candidate, relaxed) ?: continue
                if (!transition.clear) {
                    continue
                }
                val candidateCost = traversalCost(level, snapshot, current, candidate, offset.baseCost, transition.type)
                val existing = neighbors[candidate]
                if (existing == null || candidateCost < existing) {
                    neighbors[candidate] = candidateCost
                }
            }
        }
        for (edgeCandidate in findEdgeDescentCandidates(level, player, snapshot, current, capabilities)) {
            val candidate = edgeCandidate.landing
            if (candidate == current ||
                abs(candidate.x - start.x) > searchRadius ||
                abs(candidate.z - start.z) > searchRadius
            ) {
                continue
            }
            val baseCost = max(1.0, horizontalDistance(nodeCenter(level, snapshot, candidate).subtract(nodeCenter(level, snapshot, current))))
            val candidateCost = traversalCost(level, snapshot, current, candidate, baseCost, edgeCandidate.type) + edgeCandidate.reach * 0.08
            val existing = neighbors[candidate]
            if (existing == null || candidateCost < existing) {
                neighbors[candidate] = candidateCost
            }
        }
        return neighbors.entries.map { it.key to it.value }
    }

    private fun resolvePlannerTransitionType(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        from: BlockPos,
        to: BlockPos,
        relaxed: Boolean
    ): TransitionType? {
        val direct = resolveTransition(level, player, snapshot, from, to, relaxed)
        if (direct != null && direct.clear) {
            return direct.type
        }
        val capabilities = detectTraversalCapabilities(detectMovementProfile(level, player, nodeCenter(level, snapshot, from)))
        return findEdgeDescentCandidates(level, player, snapshot, from, capabilities)
            .firstOrNull { it.landing == to }
            ?.type
    }

    private fun canTraverseDiagonal(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        current: BlockPos,
        offset: NodeOffset,
        capabilities: TraversalCapabilities
    ): Boolean {
        val xCandidate = findTransitionCandidate(level, player, snapshot, current.offset(offset.dx, 0, 0), current.y, capabilities)
        val zCandidate = findTransitionCandidate(level, player, snapshot, current.offset(0, 0, offset.dz), current.y, capabilities)
        return xCandidate != null && zCandidate != null
    }

    private fun findTransitionCandidate(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        base: BlockPos,
        currentY: Int,
        capabilities: TraversalCapabilities
    ): BlockPos? {
        for (yOffset in candidateYOffsets(capabilities)) {
            val candidate = base.offset(0, yOffset, 0)
            if (candidate.y - currentY > capabilities.maxJumpUp || currentY - candidate.y > capabilities.maxSafeDrop) {
                continue
            }
            if (isStandable(level, player, snapshot, candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun resolveTransition(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        from: BlockPos,
        to: BlockPos,
        relaxed: Boolean
    ): TransitionCheck? {
        val capabilities = detectTraversalCapabilities(detectMovementProfile(level, player, nodeCenter(level, snapshot, from)))
        val deltaY = to.y - from.y
        val supportDelta = supportSurfaceY(level, snapshot, to) - supportSurfaceY(level, snapshot, from)
        val horizontal = horizontalDistance(nodeCenter(level, snapshot, to).subtract(nodeCenter(level, snapshot, from)))
        val smoothStep = isSmoothWalkSurface(level, from.below(), level.getBlockState(from.below())) ||
            isSmoothWalkSurface(level, to.below(), level.getBlockState(to.below()))
        return when {
            deltaY > capabilities.maxJumpUp || deltaY < -capabilities.maxSafeDrop -> null
            smoothStep && supportDelta <= 0.76 && supportDelta >= -0.64 -> TransitionCheck(
                TransitionType.WALK,
                if (relaxed) hasRelaxedLineClearance(level, player, nodeCenter(level, snapshot, from), nodeCenter(level, snapshot, to), 0.88) else hasLineClearance(level, player, nodeCenter(level, snapshot, from), nodeCenter(level, snapshot, to), 0.92)
            )
            supportDelta < -0.18 && supportDelta >= -1.18 && horizontal <= 1.9 -> TransitionCheck(
                TransitionType.STEP_DOWN,
                if (relaxed) hasRelaxedStepDownClearance(level, player, from, to) else hasStepDownClearance(level, player, from, to)
            )
            supportDelta > 0.60 && supportDelta <= capabilities.maxJumpHeight + 0.08 -> TransitionCheck(
                TransitionType.JUMP,
                if (relaxed) hasRelaxedJumpClearance(level, player, from, to, capabilities) else hasJumpClearance(level, player, from, to, capabilities)
            )
            supportDelta < -0.60 -> TransitionCheck(
                TransitionType.DROP,
                if (relaxed) hasRelaxedDropClearance(level, player, from, to) else hasDropClearance(level, player, from, to)
            )
            else -> TransitionCheck(
                TransitionType.WALK,
                if (relaxed) hasRelaxedLineClearance(level, player, nodeCenter(level, from), nodeCenter(level, to)) else hasLineClearance(level, player, nodeCenter(level, from), nodeCenter(level, to))
            )
        }
    }

    private fun hasJumpClearance(level: ClientLevel, player: LocalPlayer, from: BlockPos, to: BlockPos, capabilities: TraversalCapabilities): Boolean {
        val start = nodeCenter(level, from)
        val end = nodeCenter(level, to)
        val supportDelta = (supportSurfaceY(level, to) - supportSurfaceY(level, from)).coerceAtLeast(0.0)
        val simulated = simulateJumpFeasibility(level, player, from, to, capabilities)
        if (simulated.clear) {
            return true
        }
        if (hasShortJumpClearance(level, player, start, end, supportDelta, capabilities)) {
            return true
        }
        val midpoint = start.lerp(end, 0.5).add(0.0, capabilities.jumpArcHeight + max(0.0, supportDelta - 1.0) * 0.34, 0.0)
        return isPolylineClear(level, player, buildBezierSamples(start, midpoint, end))
    }

    private fun hasRelaxedJumpClearance(level: ClientLevel, player: LocalPlayer, from: BlockPos, to: BlockPos, capabilities: TraversalCapabilities): Boolean {
        return hasJumpClearance(level, player, from, to, capabilities) ||
            hasRelaxedLineClearance(level, player, nodeCenter(level, from), nodeCenter(level, to).add(0.0, 0.45, 0.0))
    }

    private fun hasShortJumpClearance(
        level: ClientLevel,
        player: LocalPlayer,
        start: Vec3,
        end: Vec3,
        supportDelta: Double,
        capabilities: TraversalCapabilities
    ): Boolean {
        val horizontal = horizontalDistance(end.subtract(start))
        if (horizontal > 1.95 || supportDelta !in 0.42..(capabilities.maxJumpHeight + 0.14)) {
            return false
        }
        val launch = start.add(0.0, 0.10, 0.0)
        val landing = end.add(0.0, 0.12, 0.0)
        val control = launch.lerp(landing, 0.5).add(0.0, max(capabilities.jumpArcHeight * 0.84, supportDelta + 0.76), 0.0)
        return isPolylineClear(level, player, buildBezierSamples(launch, control, landing), widthScale = 0.74) ||
            hasRelaxedLineClearance(level, player, launch.add(0.0, 0.82, 0.0), landing.add(0.0, 0.88, 0.0), 0.66)
    }

    private fun hasDropClearance(level: ClientLevel, player: LocalPlayer, from: BlockPos, to: BlockPos): Boolean {
        val simulated = simulateDropFeasibility(level, player, from, to, controlled = false)
        if (simulated.clear) {
            return true
        }
        val start = descentExecutePoint(level, from, to).add(0.0, 0.10, 0.0)
        val edgeFall = start.lerp(nodeCenter(level, to), 0.35).add(0.0, -0.18, 0.0)
        val end = nodeCenter(level, to).add(0.0, 0.08, 0.0)
        return isPolylineClear(level, player, listOf(start, edgeFall, end), widthScale = 0.84)
    }

    private fun hasStepDownClearance(level: ClientLevel, player: LocalPlayer, from: BlockPos, to: BlockPos): Boolean {
        val simulated = simulateDropFeasibility(level, player, from, to, controlled = true)
        if (simulated.clear) {
            return true
        }
        val start = descentExecutePoint(level, from, to).add(0.0, 0.05, 0.0)
        val edgeFall = start.lerp(nodeCenter(level, to), 0.46).add(0.0, -0.08, 0.0)
        val end = nodeCenter(level, to).add(0.0, 0.06, 0.0)
        return isPolylineClear(level, player, listOf(start, edgeFall, end), widthScale = 0.78) ||
            hasRelaxedDropClearance(level, player, from, to)
    }

    private fun hasRelaxedStepDownClearance(level: ClientLevel, player: LocalPlayer, from: BlockPos, to: BlockPos): Boolean {
        val start = descentExecutePoint(level, from, to).add(0.0, 0.04, 0.0)
        val edgeFall = start.lerp(nodeCenter(level, to), 0.5).add(0.0, -0.10, 0.0)
        val end = nodeCenter(level, to).add(0.0, 0.05, 0.0)
        return isPolylineClear(level, player, listOf(start, edgeFall, end), widthScale = 0.68) ||
            hasRelaxedDropClearance(level, player, from, to)
    }

    private fun simulateJumpFeasibility(
        level: ClientLevel,
        player: LocalPlayer,
        from: BlockPos,
        to: BlockPos,
        capabilities: TraversalCapabilities
    ): TransitionFeasibility {
        val start = nodeCenter(level, from).add(0.0, 0.02, 0.0)
        val end = nodeCenter(level, to).add(0.0, 0.02, 0.0)
        val samples = simulateBallisticSamples(
            start = start,
            end = end,
            horizontalSpeed = capabilities.preferredAirSpeed,
            initialVerticalVelocity = capabilities.jumpImpulse,
            maxTicks = 14
        ) ?: return TransitionFeasibility(false, 0.0, 0.0, capabilities.preferredGroundSpeed)
        val landingScore = landingSupportScore(level, to)
        val clear = landingScore >= 0.34 && sweepSamplesClear(level, player, samples, 0.76)
        return TransitionFeasibility(clear, landingScore, sampleHeadroom(level, player, samples), capabilities.preferredGroundSpeed)
    }

    private fun simulateDropFeasibility(
        level: ClientLevel,
        player: LocalPlayer,
        from: BlockPos,
        to: BlockPos,
        controlled: Boolean
    ): TransitionFeasibility {
        val start = nodeCenter(level, from).add(0.0, 0.04, 0.0)
        val end = nodeCenter(level, to).add(0.0, 0.04, 0.0)
        val horizontalSpeed = if (controlled) 0.18 else 0.22
        val samples = simulateBallisticSamples(
            start = start,
            end = end,
            horizontalSpeed = horizontalSpeed,
            initialVerticalVelocity = if (controlled) -0.02 else -0.08,
            maxTicks = if (controlled) 10 else 16
        ) ?: return TransitionFeasibility(false, 0.0, 0.0, horizontalSpeed)
        val landingScore = landingSupportScore(level, to)
        val clear = landingScore >= (if (controlled) 0.28 else 0.22) &&
            sweepSamplesClear(level, player, samples, if (controlled) 0.82 else 0.88)
        return TransitionFeasibility(clear, landingScore, sampleHeadroom(level, player, samples), horizontalSpeed)
    }

    private fun descentExecutePoint(level: ClientLevel, from: BlockPos, to: BlockPos, preferredDirection: Vec3? = null): Vec3 {
        val fromCenter = nodeCenter(level, from)
        val towardLanding = normalize(preferredDirection ?: Vec3((to.x - from.x).toDouble(), 0.0, (to.z - from.z).toDouble()))
        return if (towardLanding == Vec3.ZERO) {
            fromCenter
        } else {
            fromCenter.add(towardLanding.scale(0.48))
        }
    }

    private fun simulateBallisticSamples(
        start: Vec3,
        end: Vec3,
        horizontalSpeed: Double,
        initialVerticalVelocity: Double,
        maxTicks: Int
    ): List<Vec3>? {
        val horizontal = Vec3(end.x - start.x, 0.0, end.z - start.z)
        val horizontalDistance = horizontal.length()
        val ticks = ceil(horizontalDistance / max(0.08, horizontalSpeed)).toInt().coerceIn(2, maxTicks)
        val stepX = (end.x - start.x) / ticks.toDouble()
        val stepZ = (end.z - start.z) / ticks.toDouble()
        val samples = mutableListOf(start)
        var x = start.x
        var y = start.y
        var z = start.z
        var velocityY = initialVerticalVelocity
        for (tick in 1..maxTicks) {
            x += stepX
            y += velocityY
            z += stepZ
            samples += Vec3(x, y, z)
            if (tick >= ticks && abs(x - end.x) <= 0.36 && abs(z - end.z) <= 0.36 && y <= end.y + 0.26) {
                samples += end
                return samples
            }
            velocityY = (velocityY - 0.08) * 0.98
            if (y < end.y - 1.8) {
                break
            }
        }
        return null
    }

    private fun sweepSamplesClear(level: ClientLevel, player: LocalPlayer, samples: List<Vec3>, widthScale: Double): Boolean {
        if (samples.isEmpty()) {
            return false
        }
        for (index in 0 until samples.lastIndex) {
            val from = samples[index]
            val to = samples[index + 1]
            val distance = from.distanceTo(to)
            val steps = max(1, ceil(distance / 0.14).toInt())
            for (step in 0..steps) {
                val sample = from.lerp(to, step.toDouble() / steps.toDouble())
                if (!level.noCollision(player, playerBox(player, sample, widthScale))) {
                    return false
                }
            }
        }
        return true
    }

    private fun sampleHeadroom(level: ClientLevel, player: LocalPlayer, samples: List<Vec3>): Double {
        var minimum = Double.POSITIVE_INFINITY
        for (sample in samples) {
            var clearance = 0.0
            while (clearance <= 1.8) {
                val probe = sample.add(0.0, clearance, 0.0)
                if (!level.noCollision(player, playerBox(player, probe, 0.62))) {
                    break
                }
                clearance += 0.18
            }
            minimum = min(minimum, clearance)
        }
        return if (minimum == Double.POSITIVE_INFINITY) 0.0 else minimum
    }

    private fun landingSupportScore(level: ClientLevel, standableNode: BlockPos): Double {
        return landingSupportScore(level, null, standableNode)
    }

    private fun landingSupportScore(level: ClientLevel, snapshot: TerrainSnapshot?, standableNode: BlockPos): Double {
        snapshot?.node(standableNode)?.let { return it.supportScore }
        val floorPos = standableNode.below()
        val floorState = level.getBlockState(floorPos)
        val shape = floorState.getCollisionShape(level, floorPos)
        if (shape.isEmpty) {
            return 0.0
        }
        val bounds = shape.bounds()
        val widthX = (bounds.maxX - bounds.minX).coerceIn(0.0, 1.0)
        val widthZ = (bounds.maxZ - bounds.minZ).coerceIn(0.0, 1.0)
        var score = (bounds.maxY.coerceIn(0.0, 1.0) * widthX * widthZ).coerceIn(0.0, 1.0)
        if (isSmoothWalkSurface(level, floorPos, floorState)) {
            score += 0.12
        }
        return score.coerceIn(0.0, 1.0)
    }

    private fun captureTerrainSnapshot(
        level: ClientLevel,
        player: LocalPlayer,
        start: BlockPos,
        target: BlockPos,
        searchConfig: SearchConfig,
        capabilities: TraversalCapabilities
    ): TerrainSnapshot {
        val horizontalDistance = max(abs(target.x - start.x), abs(target.z - start.z))
        val margin = min(searchConfig.horizontalRadius, max(12, horizontalDistance / 2 + 10))
        val minX = min(start.x, target.x) - margin
        val maxX = max(start.x, target.x) + margin
        val minZ = min(start.z, target.z) - margin
        val maxZ = max(start.z, target.z) + margin
        val minY = min(start.y, target.y) - (capabilities.maxSafeDrop + GOAL_SEARCH_DOWN + 4)
        val maxY = max(start.y, target.y) + (capabilities.maxJumpUp + GOAL_SEARCH_UP + 4)
        val nodes = HashMap<BlockPos, TerrainNodeSnapshot>((maxX - minX + 1) * (maxZ - minZ + 1))
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in minY..maxY) {
                    val position = BlockPos(x, y, z)
                    val standable = isStandable(level, player, null, position)
                    val supportY = supportSurfaceY(level, null, position)
                    val supportScore = landingSupportScore(level, null, position)
                    nodes[position] = TerrainNodeSnapshot(standable = standable, supportY = supportY, supportScore = supportScore)
                }
            }
        }
        return TerrainSnapshot(minX = minX, maxX = maxX, minY = minY, maxY = maxY, minZ = minZ, maxZ = maxZ, nodes = nodes)
    }

    private fun hasRelaxedDropClearance(level: ClientLevel, player: LocalPlayer, from: BlockPos, to: BlockPos): Boolean {
        return hasDropClearance(level, player, from, to) ||
            hasRelaxedLineClearance(level, player, nodeCenter(level, from).add(0.0, 0.12, 0.0), nodeCenter(level, to).add(0.0, 0.08, 0.0), 0.82)
    }

    private fun isStandable(level: ClientLevel, player: LocalPlayer, position: BlockPos): Boolean {
        return isStandable(level, player, null, position)
    }

    private fun isStandable(level: ClientLevel, player: LocalPlayer, snapshot: TerrainSnapshot?, position: BlockPos): Boolean {
        snapshot?.node(position)?.let { return it.standable }
        val feetState = level.getBlockState(position)
        val headState = level.getBlockState(position.above())
        val floorPos = position.below()
        val floorState = level.getBlockState(floorPos)
        if (!isPassableSpace(level, position, feetState) || !isPassableSpace(level, position.above(), headState)) {
            return false
        }
        if (isHazard(feetState) || isHazard(headState) || isHazardousFloor(floorState)) {
            return false
        }
        if (floorState.getCollisionShape(level, floorPos).isEmpty) {
            return false
        }
        return level.noCollision(player, playerBox(player, nodeCenter(level, position)))
    }

    private fun isPassableSpace(level: ClientLevel, position: BlockPos, state: BlockState): Boolean {
        if (isHazard(state)) {
            return false
        }
        if (state.isAir || state.liquid()) {
            return !state.fluidState.`is`(Fluids.LAVA) && !state.fluidState.`is`(Fluids.FLOWING_LAVA)
        }
        if (!state.getCollisionShape(level, position).isEmpty) {
            return false
        }
        return !state.blocksMotion()
    }

    private fun isHazardousFloor(state: BlockState): Boolean {
        return isHazard(state)
    }

    private fun isSmoothWalkSurface(level: ClientLevel, position: BlockPos, state: BlockState): Boolean {
        val block = state.block
        if (block is SlabBlock || block is StairBlock) {
            return true
        }
        val shape = state.getCollisionShape(level, position)
        return !shape.isEmpty && shape.bounds().maxY < 1.0
    }

    private fun isHazard(state: BlockState): Boolean {
        val block = state.block
        return state.fluidState.`is`(Fluids.LAVA) ||
            state.fluidState.`is`(Fluids.FLOWING_LAVA) ||
            block is BaseFireBlock ||
            block is CampfireBlock ||
            block is CactusBlock ||
            block is SweetBerryBushBlock ||
            block is WitherRoseBlock ||
            block is PowderSnowBlock ||
            block is MagmaBlock
    }

    private fun findStandableNode(
        level: ClientLevel,
        player: LocalPlayer,
        origin: BlockPos,
        searchRadius: Int,
        searchUp: Int,
        searchDown: Int
    ): BlockPos? {
        var best: BlockPos? = null
        var bestScore = Double.POSITIVE_INFINITY
        for (xOffset in -searchRadius..searchRadius) {
            for (zOffset in -searchRadius..searchRadius) {
                for (yOffset in searchUp downTo -searchDown) {
                    val candidate = origin.offset(xOffset, yOffset, zOffset)
                    if (!isStandable(level, player, candidate)) {
                        continue
                    }
                    val score = nodeCenter(level, candidate).distanceTo(player.position())
                    if (score < bestScore) {
                        best = candidate
                        bestScore = score
                    }
                }
            }
        }
        return best
    }

    private fun candidateYOffsets(capabilities: TraversalCapabilities): List<Int> {
        val offsets = mutableListOf<Int>()
        offsets += 0
        for (down in 1..capabilities.maxSafeDrop) {
            offsets += -down
            if (down <= capabilities.maxJumpUp) {
                offsets += down
            }
        }
        for (up in 2..capabilities.maxJumpUp) {
            if (up !in offsets) {
                offsets += up
            }
        }
        return offsets.distinct()
    }

    private fun buildRouteActions(level: ClientLevel, pathNodes: List<BlockPos>): List<RouteAction> {
        if (pathNodes.size <= 1) {
            return emptyList()
        }
        return pathNodes.zipWithNext().mapIndexed { index, (from, to) ->
            val previous = pathNodes.getOrNull(index - 1) ?: from
            val next = pathNodes.getOrNull(index + 2) ?: to
            val executeCenter = nodeCenter(level, from)
            val landingCenter = nodeCenter(level, to)
            val supportDelta = supportSurfaceY(level, to) - supportSurfaceY(level, from)
            val type = when {
                supportDelta > 0.6 -> RouteActionType.JUMP
                supportDelta < -1.18 -> RouteActionType.DROP
                supportDelta < -0.34 -> RouteActionType.STEP_DOWN
                else -> RouteActionType.WALK
            }
            val executePoint = when (type) {
                RouteActionType.JUMP,
                RouteActionType.DROP,
                RouteActionType.STEP_DOWN -> descentExecutePoint(level, from, to)
                RouteActionType.WALK -> landingCenter
            }
            val landingPoint = when (type) {
                RouteActionType.JUMP,
                RouteActionType.DROP,
                RouteActionType.STEP_DOWN,
                RouteActionType.WALK -> landingCenter
            }
            val aimPoint = when (type) {
                RouteActionType.JUMP -> executePoint.lerp(landingPoint, 0.72)
                RouteActionType.DROP -> executePoint.lerp(landingPoint, 0.86)
                RouteActionType.STEP_DOWN -> executePoint.lerp(landingPoint, 0.74)
                RouteActionType.WALK -> landingPoint
            }
            val entryTangent = normalize(executeCenter.subtract(nodeCenter(level, previous)))
            val exitTangent = normalize(nodeCenter(level, next).subtract(landingCenter))
            val commitmentDistance = when (type) {
                RouteActionType.JUMP -> 1.34
                RouteActionType.DROP -> 0.98
                RouteActionType.STEP_DOWN -> 0.86
                RouteActionType.WALK -> 0.0
            }
            val completionProgress = when (type) {
                RouteActionType.JUMP -> 0.82
                RouteActionType.DROP -> 0.88
                RouteActionType.STEP_DOWN -> 0.76
                RouteActionType.WALK -> 1.0
            }
            val lateralTolerance = when (type) {
                RouteActionType.JUMP -> 0.62
                RouteActionType.DROP -> 0.70
                RouteActionType.STEP_DOWN -> 0.56
                RouteActionType.WALK -> 0.42
            }
            val expectedSupportY = supportSurfaceY(level, to)
            val minimumRequiredSpeed = when (type) {
                RouteActionType.JUMP -> 0.18
                RouteActionType.DROP -> 0.10
                RouteActionType.STEP_DOWN -> 0.08
                RouteActionType.WALK -> 0.0
            }
            val preferredSpeed = when (type) {
                RouteActionType.JUMP -> 0.26
                RouteActionType.DROP -> 0.20
                RouteActionType.STEP_DOWN -> 0.18
                RouteActionType.WALK -> 0.16
            }
            val headClearance = when (type) {
                RouteActionType.JUMP -> 1.55
                RouteActionType.DROP -> 1.25
                RouteActionType.STEP_DOWN -> 1.15
                RouteActionType.WALK -> 1.05
            }
            RouteAction(
                type = type,
                executePoint = executePoint,
                landingPoint = landingPoint,
                aimPoint = aimPoint,
                segmentIndex = index,
                entryTangent = entryTangent,
                exitTangent = exitTangent,
                commitmentDistance = commitmentDistance,
                completionProgress = completionProgress,
                lateralTolerance = lateralTolerance,
                expectedSupportY = expectedSupportY,
                minimumRequiredSpeed = minimumRequiredSpeed,
                preferredSpeed = preferredSpeed,
                headClearance = headClearance
            )
        }
    }

    private fun simplifyPath(level: ClientLevel, player: LocalPlayer, path: List<BlockPos>): List<BlockPos> {
        if (path.size <= 2) {
            return path
        }

        val simplified = mutableListOf(path.first())
        var anchorIndex = 0
        var probeIndex = 2
        while (probeIndex < path.size) {
            val anchor = path[anchorIndex]
            val probe = path[probeIndex]
            if (!isMajorSegmentTraversable(level, player, anchor, probe)) {
                simplified += path[probeIndex - 1]
                anchorIndex = probeIndex - 1
            }
            probeIndex++
        }
        if (simplified.last() != path.last()) {
            simplified += path.last()
        }
        return simplified
    }

    private fun buildFollowRouteNodes(level: ClientLevel, path: List<BlockPos>): List<BlockPos> {
        if (path.size <= 2) {
            return expandFlatRouteAnchors(level, path)
        }

        val display = mutableListOf(path.first())
        var lastKept = path.first()
        for (index in 1 until path.lastIndex) {
            val previous = path[index - 1]
            val current = path[index]
            val next = path[index + 1]
            val incoming = directionDelta(previous, current)
            val outgoing = directionDelta(current, next)
            val supportShift = abs(supportSurfaceY(level, current) - supportSurfaceY(level, previous)) > 0.24 ||
                abs(supportSurfaceY(level, next) - supportSurfaceY(level, current)) > 0.24
            val constrained = opennessPenalty(level, current) >= 0.08
            val stride = horizontalDistance(nodeCenter(level, current).subtract(nodeCenter(level, lastKept)))
            val verticalAction = current.y != previous.y || next.y != current.y
            if (incoming != outgoing || supportShift || constrained || verticalAction || stride >= FLAT_ROUTE_POINT_SPACING) {
                display += current
                lastKept = current
            }
        }
        if (display.last() != path.last()) {
            display += path.last()
        }
        return expandFlatRouteAnchors(level, display.distinct())
    }

    private fun expandFlatRouteAnchors(level: ClientLevel, anchors: List<BlockPos>): List<BlockPos> {
        if (anchors.size <= 1) {
            return anchors
        }

        val expanded = mutableListOf(anchors.first())
        for (index in 0 until anchors.lastIndex) {
            val from = anchors[index]
            val to = anchors[index + 1]
            val fromCenter = nodeCenter(level, from)
            val toCenter = nodeCenter(level, to)
            val horizontalSpan = horizontalDistance(toCenter.subtract(fromCenter))
            val verticalShift = abs(toCenter.y - fromCenter.y)
            if (verticalShift <= 0.10 && horizontalSpan > FLAT_ROUTE_POINT_SPACING * 1.2) {
                val steps = max(1, kotlin.math.floor(horizontalSpan / FLAT_ROUTE_POINT_SPACING).toInt())
                for (step in 1 until steps) {
                    val progress = step.toDouble() / steps.toDouble()
                    expanded += BlockPos.containing(fromCenter.lerp(toCenter, progress))
                }
            }
            expanded += to
        }
        return expanded.distinct()
    }

    private fun isMajorSegmentTraversable(level: ClientLevel, player: LocalPlayer, from: BlockPos, to: BlockPos): Boolean {
        val capabilities = detectTraversalCapabilities(detectMovementProfile(level, player, nodeCenter(level, from)))
        val supportDelta = supportSurfaceY(level, to) - supportSurfaceY(level, from)
        return when {
            supportDelta > 0.60 -> hasRelaxedJumpClearance(level, player, from, to, capabilities)
            supportDelta < -0.34 && supportDelta >= -1.18 -> hasRelaxedStepDownClearance(level, player, from, to)
            supportDelta < -1.18 -> hasRelaxedDropClearance(level, player, from, to)
            else -> abs(to.y - from.y) <= capabilities.maxJumpUp &&
                abs(from.y - to.y) <= capabilities.maxSafeDrop &&
                hasRelaxedLineClearance(level, player, nodeCenter(level, from), nodeCenter(level, to))
        }
    }

    private fun extractMajorRouteNodes(level: ClientLevel, path: List<BlockPos>): List<BlockPos> {
        if (path.size <= 2) {
            return path
        }

        val extracted = mutableListOf(path.first())
        var lastKept = path.first()
        for (index in 1 until path.lastIndex) {
            val previous = path[index - 1]
            val current = path[index]
            val next = path[index + 1]
            val incoming = directionDelta(previous, current)
            val outgoing = directionDelta(current, next)
            val jumpEdge = current.y > previous.y || next.y > current.y
            val rotationEdge = incoming != outgoing
            val dropEdge = current.y < previous.y || next.y < current.y
            val constrained = opennessPenalty(level, current) >= 0.10
            val supportShift = abs(supportSurfaceY(level, current) - supportSurfaceY(level, previous)) > 0.34 ||
                abs(supportSurfaceY(level, next) - supportSurfaceY(level, current)) > 0.34
            val longStride = horizontalDistance(nodeCenter(level, current).subtract(nodeCenter(level, lastKept))) >= 2.8
            if (rotationEdge || jumpEdge || dropEdge || constrained || supportShift || longStride) {
                extracted += current
                lastKept = current
            }
        }
        extracted += path.last()
        return extracted.distinct()
    }

    private fun directionDelta(from: BlockPos, to: BlockPos): Triple<Int, Int, Int> {
        return Triple((to.x - from.x).signValue(), (to.y - from.y).signValue(), (to.z - from.z).signValue())
    }

    private fun Int.signValue(): Int = when {
        this > 0 -> 1
        this < 0 -> -1
        else -> 0
    }

    private fun buildSmoothedSamples(level: ClientLevel, player: LocalPlayer, anchors: List<Vec3>): List<Vec3> {
        if (anchors.isEmpty()) {
            return emptyList()
        }
        if (anchors.size == 1) {
            return anchors
        }

        val samples = mutableListOf(anchors.first())
        var tail = anchors.first()
        for (index in 1 until anchors.lastIndex) {
            val previous = anchors[index - 1]
            val current = anchors[index]
            val next = anchors[index + 1]
            val incoming = current.subtract(previous)
            val outgoing = next.subtract(current)
            val incomingLength = incoming.length()
            val outgoingLength = outgoing.length()
            val turnAmount = abs(Mth.wrapDegrees(desiredYawTo(previous, current) - desiredYawTo(current, next)))
            val verticalTransition = abs(current.y - previous.y) > 0.24 || abs(next.y - current.y) > 0.24
            val constrainedCorner = !hasRelaxedLineClearance(level, player, previous, next, 0.78)
            if (turnAmount < 6.0f || incomingLength <= 0.18 || outgoingLength <= 0.18 || verticalTransition || constrainedCorner) {
                appendPolylineSamples(samples, tail, current, SAMPLE_SPACING)
                tail = current
                continue
            }

            val cornerRadius = min(CORNER_RADIUS, min(incomingLength, outgoingLength) * 0.52)
            val entry = current.subtract(normalize(incoming).scale(cornerRadius))
            val exit = current.add(normalize(outgoing).scale(cornerRadius))
            appendPolylineSamples(samples, tail, entry, SAMPLE_SPACING)

            val curveSamples = buildBezierSamples(entry, current, exit)
            if (isPolylineClear(level, player, curveSamples)) {
                appendDistinct(samples, curveSamples)
                tail = exit
            } else {
                appendPolylineSamples(samples, entry, current, SAMPLE_SPACING)
                appendPolylineSamples(samples, current, exit, SAMPLE_SPACING)
                tail = exit
            }
        }

        appendPolylineSamples(samples, tail, anchors.last(), SAMPLE_SPACING)
        if (samples.last().distanceTo(anchors.last()) > 0.02) {
            samples += anchors.last()
        }
        return samples
    }

    private fun buildBezierSamples(start: Vec3, control: Vec3, end: Vec3): List<Vec3> {
        val points = mutableListOf(start)
        for (step in 1..16) {
            val t = step / 16.0
            val inv = 1.0 - t
            points += Vec3(
                inv * inv * start.x + 2.0 * inv * t * control.x + t * t * end.x,
                inv * inv * start.y + 2.0 * inv * t * control.y + t * t * end.y,
                inv * inv * start.z + 2.0 * inv * t * control.z + t * t * end.z
            )
        }
        return points
    }

    private fun appendPolylineSamples(target: MutableList<Vec3>, start: Vec3, end: Vec3, spacing: Double) {
        val distance = start.distanceTo(end)
        val steps = max(1, ceil(distance / spacing).toInt())
        for (step in 1..steps) {
            val progress = step.toDouble() / steps.toDouble()
            appendDistinct(target, listOf(start.lerp(end, progress)))
        }
    }

    private fun appendDistinct(target: MutableList<Vec3>, additions: List<Vec3>) {
        for (point in additions) {
            if (target.isEmpty() || target.last().distanceTo(point) > 1.0e-3) {
                target += point
            }
        }
    }

    private fun isPolylineClear(level: ClientLevel, player: LocalPlayer, samples: List<Vec3>, widthScale: Double = 1.0): Boolean {
        for (index in 0 until samples.lastIndex) {
            if (!hasLineClearance(level, player, samples[index], samples[index + 1], widthScale)) {
                return false
            }
        }
        return true
    }

    private fun buildRenderBoxes(level: ClientLevel, pathNodes: List<BlockPos>): List<Pair<AABB, WorldBoxRenderer.BoxStyle>> {
        if (pathNodes.isEmpty()) {
            return emptyList()
        }

        val boxes = mutableListOf<Pair<AABB, WorldBoxRenderer.BoxStyle>>()
        pathNodes.forEachIndexed { index, node ->
            boxes += routeMarkerBox(node, if (index == pathNodes.lastIndex) 0.03 else 0.06) to turnBlockStyle
        }
        return boxes
    }

    private fun buildRenderQuads(level: ClientLevel, pathNodes: List<BlockPos>): List<WorldBoxRenderer.SurfaceQuad> {
        if (pathNodes.size <= 1) {
            return emptyList()
        }
        return (0 until pathNodes.lastIndex).mapNotNull { index ->
            val start = nodeCenter(level, pathNodes[index]).add(0.0, ROUTE_SURFACE_Y_OFFSET, 0.0)
            val end = nodeCenter(level, pathNodes[index + 1]).add(0.0, ROUTE_SURFACE_Y_OFFSET, 0.0)
            buildConnectorQuad(start, end)
        }
    }

    private fun buildConnectorQuad(start: Vec3, end: Vec3): WorldBoxRenderer.SurfaceQuad? {
        val horizontal = Vec3(end.x - start.x, 0.0, end.z - start.z)
        val horizontalLength = horizontal.length()
        if (horizontalLength <= 1.0e-4) {
            return null
        }
        val perpendicular = Vec3(
            -horizontal.z / horizontalLength * ROUTE_LINE_HALF_WIDTH,
            0.0,
            horizontal.x / horizontalLength * ROUTE_LINE_HALF_WIDTH
        )
        return WorldBoxRenderer.SurfaceQuad(
            a = start.add(perpendicular),
            b = start.subtract(perpendicular),
            c = end.subtract(perpendicular),
            d = end.add(perpendicular),
            style = lineSegmentStyle
        )
    }

    private fun routeMarkerBox(standableNode: BlockPos, inset: Double): AABB {
        val floor = standableNode.below()
        return AABB(
            floor.x + inset,
            floor.y + inset,
            floor.z + inset,
            floor.x + 1.0 - inset,
            floor.y + 1.0 - inset,
            floor.z + 1.0 - inset
        )
    }

    private fun projectOntoRoute(samples: List<Vec3>, position: Vec3, hintIndex: Int): RouteProjection {
        if (samples.size == 1) {
            return RouteProjection(0, samples.first(), Vec3.ZERO, position.distanceTo(samples.first()), 0.0)
        }

        var bestProjection: RouteProjection? = null
        var bestScore = Double.POSITIVE_INFINITY
        val scanStart = (hintIndex - 10).coerceAtLeast(0)
        val scanEnd = (hintIndex + 112).coerceAtMost(samples.lastIndex - 1)
        val fullScan = hintIndex <= 0 || hintIndex >= samples.lastIndex - 1
        val indices = if (fullScan) 0..samples.lastIndex - 1 else scanStart..scanEnd

        for (index in indices) {
            val start = samples[index]
            val end = samples[index + 1]
            val segment = end.subtract(start)
            val segmentLengthSqr = segment.lengthSqr()
            if (segmentLengthSqr <= 1.0e-6) {
                continue
            }
            val projectionScale = ((position.subtract(start)).dot(segment) / segmentLengthSqr).coerceIn(0.0, 1.0)
            val projectionPoint = start.add(segment.scale(projectionScale))
            val distance = position.distanceTo(projectionPoint)
            val backwardsPenalty = max(0, hintIndex - index) * 0.11
            val score = distance + backwardsPenalty
            if (bestProjection == null || score < bestScore) {
                bestScore = score
                bestProjection = RouteProjection(
                    segmentIndex = index,
                    point = projectionPoint,
                    tangent = normalize(segment),
                    distanceToRoute = distance,
                    remainingDistance = remainingDistance(samples, index, projectionPoint)
                )
            }
        }

        return bestProjection ?: RouteProjection(
            0,
            samples.first(),
            normalize(samples[1].subtract(samples.first())),
            position.distanceTo(samples.first()),
            remainingDistance(samples, 0, samples.first())
        )
    }

    private fun remainingDistance(samples: List<Vec3>, index: Int, pointOnSegment: Vec3): Double {
        var total = pointOnSegment.distanceTo(samples[index + 1])
        for (cursor in index + 1 until samples.lastIndex) {
            total += samples[cursor].distanceTo(samples[cursor + 1])
        }
        return total
    }

    private fun pointAlongRoute(samples: List<Vec3>, projection: RouteProjection, distance: Double): Vec3 {
        var remaining = distance
        var currentPoint = projection.point
        var index = projection.segmentIndex
        while (index < samples.lastIndex) {
            val next = samples[index + 1]
            val segmentLength = currentPoint.distanceTo(next)
            if (segmentLength >= remaining) {
                val alpha = if (segmentLength <= 1.0e-5) 1.0 else remaining / segmentLength
                return currentPoint.lerp(next, alpha)
            }
            remaining -= segmentLength
            index++
            currentPoint = next
        }
        return samples.last()
    }

    private fun pointAlongSamples(samples: List<Vec3>, startIndex: Int, startPoint: Vec3, distance: Double): Vec3 {
        var remaining = distance
        var currentPoint = startPoint
        var index = startIndex
        while (index < samples.lastIndex) {
            val next = samples[index + 1]
            val segmentLength = currentPoint.distanceTo(next)
            if (segmentLength >= remaining) {
                val alpha = if (segmentLength <= 1.0e-5) 1.0 else remaining / segmentLength
                return currentPoint.lerp(next, alpha)
            }
            remaining -= segmentLength
            index++
            currentPoint = next
        }
        return samples.last()
    }

    private fun upcomingTurnAngle(samples: List<Vec3>, segmentIndex: Int): Float {
        if (segmentIndex >= samples.lastIndex - 1) {
            return 0.0f
        }
        val current = samples[segmentIndex + 1].subtract(samples[segmentIndex])
        val next = samples[segmentIndex + 2].subtract(samples[segmentIndex + 1])
        if (current.lengthSqr() <= 1.0e-6 || next.lengthSqr() <= 1.0e-6) {
            return 0.0f
        }
        return abs(Mth.wrapDegrees(desiredYawTo(Vec3.ZERO, current) - desiredYawTo(Vec3.ZERO, next)))
    }

    private fun previewUpcomingTurn(samples: List<Vec3>, projection: RouteProjection, maxDistance: Double): TurnPreview? {
        if (samples.size < 3 || projection.segmentIndex >= samples.lastIndex - 1) {
            return null
        }

        var traveled = 0.0
        var segmentStart = projection.point
        val lastPreviewSegment = min(samples.lastIndex - 2, projection.segmentIndex + 28)
        for (index in projection.segmentIndex..lastPreviewSegment) {
            val segmentEnd = samples[index + 1]
            traveled += segmentStart.distanceTo(segmentEnd)
            if (traveled > maxDistance) {
                return null
            }
            if (index >= samples.lastIndex - 2) {
                return null
            }

            val current = segmentEnd.subtract(segmentStart)
            val next = samples[index + 2].subtract(segmentEnd)
            if (current.lengthSqr() > 1.0e-6 && next.lengthSqr() > 1.0e-6) {
                val angle = abs(Mth.wrapDegrees(desiredYawTo(Vec3.ZERO, current) - desiredYawTo(Vec3.ZERO, next)))
                if (angle >= TURN_PREVIEW_MIN_ANGLE) {
                    val exitPoint = pointAlongSamples(
                        samples,
                        index + 1,
                        segmentEnd,
                        (0.42 + angle.toDouble() / 56.0).coerceIn(0.42, 1.45)
                    )
                    return TurnPreview(
                        segmentIndex = index,
                        distance = traveled,
                        angle = angle,
                        turnPoint = segmentEnd,
                        entryTangent = normalize(current),
                        exitTangent = normalize(next),
                        exitPoint = exitPoint
                    )
                }
            }
            segmentStart = segmentEnd
        }
        return null
    }

    private fun turnSetupStrength(preview: TurnPreview, profile: MovementProfile): Float {
        val window = (
            0.95 +
                profile.horizontalSpeed * 7.2 +
                preview.angle.toDouble() / 82.0
            ).coerceIn(1.05, 3.2)
        val distanceFactor = ((window - preview.distance) / window).coerceIn(0.0, 1.0)
        val angleFactor = ((preview.angle - TURN_PREVIEW_MIN_ANGLE) / 62.0f).coerceIn(0.0f, 1.0f)
        return (distanceFactor.toFloat() * angleFactor).coerceIn(0.0f, 1.0f)
    }

    private fun isRouteObstructed(
        level: ClientLevel,
        player: LocalPlayer,
        samples: List<Vec3>,
        startSegment: Int
    ): Boolean {
        if (samples.size <= 1) {
            return false
        }
        val lastSegment = min(samples.lastIndex - 1, startSegment.coerceAtLeast(0) + 18)
        for (index in startSegment.coerceAtLeast(0)..lastSegment) {
            if (!hasLineClearance(level, player, samples[index], samples[index + 1], 0.92)) {
                return true
            }
        }
        return false
    }

    private fun hasLineClearance(level: ClientLevel, player: LocalPlayer, from: Vec3, to: Vec3): Boolean {
        return hasLineClearance(level, player, from, to, widthScale = 1.0)
    }

    private fun hasRelaxedLineClearance(level: ClientLevel, player: LocalPlayer, from: Vec3, to: Vec3): Boolean {
        return hasLineClearance(level, player, from, to, widthScale = 0.72)
    }

    private fun hasRelaxedLineClearance(level: ClientLevel, player: LocalPlayer, from: Vec3, to: Vec3, widthScale: Double): Boolean {
        return hasLineClearance(level, player, from, to, widthScale)
    }

    private fun hasLineClearance(level: ClientLevel, player: LocalPlayer, from: Vec3, to: Vec3, widthScale: Double): Boolean {
        val distance = from.distanceTo(to)
        val steps = max(1, ceil(distance / 0.18).toInt())
        for (step in 0..steps) {
            val progress = step.toDouble() / steps.toDouble()
            val sample = from.lerp(to, progress)
            if (!level.noCollision(player, playerBox(player, sample, widthScale))) {
                return false
            }
        }
        return true
    }

    private fun buildDirectFallbackPath(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        start: BlockPos,
        goalNodes: Set<BlockPos>,
        searchRadius: Int,
        debug: PathDebugTrace? = null
    ): List<BlockPos>? {
        val startCenter = nodeCenter(level, snapshot, start)
        val goal = goalNodes.minByOrNull { nodeCenter(level, snapshot, it).distanceTo(startCenter) } ?: return null
        val goalCenter = nodeCenter(level, snapshot, goal)
        val openSet = java.util.PriorityQueue<PathStep>(compareBy<PathStep> { it.fScore }.thenBy { it.hScore })
        val cameFrom = HashMap<BlockPos, BlockPos>()
        val gScore = HashMap<BlockPos, Double>()
        val closed = HashSet<BlockPos>()
        val budget = min(14_000, max(900, searchRadius * 120))
        var jumpTransitions = 0
        var descentTransitions = 0

        openSet += PathStep(start, directHeuristic(level, snapshot, start, startCenter, goalCenter), 0.0)
        gScore[start] = 0.0
        var explored = 0

        while (openSet.isNotEmpty() && explored++ < budget) {
            val current = openSet.poll().position
            if (!closed.add(current)) {
                continue
            }
            if (current in goalNodes) {
                return reconstructPath(cameFrom, current)
            }

            val currentCenter = nodeCenter(level, snapshot, current)
            val currentGoalDistance = currentCenter.distanceTo(goalCenter)
            for ((neighbor, moveCost) in neighbors(level, player, snapshot, current, start, searchRadius, relaxed = true)) {
                if (neighbor in closed) {
                    continue
                }
                val transitionType = resolvePlannerTransitionType(level, player, snapshot, current, neighbor, relaxed = true) ?: continue
                when (transitionType) {
                    TransitionType.JUMP -> jumpTransitions++
                    TransitionType.STEP_DOWN,
                    TransitionType.DROP -> descentTransitions++
                    TransitionType.WALK -> Unit
                }

                val neighborCenter = nodeCenter(level, snapshot, neighbor)
                val progress = (currentGoalDistance - neighborCenter.distanceTo(goalCenter)).coerceAtLeast(0.0)
                val directnessPenalty = directDeviationPenalty(currentCenter, neighborCenter, startCenter, goalCenter) * when (transitionType) {
                    TransitionType.JUMP -> 0.52
                    TransitionType.STEP_DOWN,
                    TransitionType.DROP -> 0.70
                    TransitionType.WALK -> 1.0
                }
                val transitionBias = when (transitionType) {
                    TransitionType.WALK -> 0.02
                    TransitionType.STEP_DOWN -> -0.10
                    TransitionType.JUMP -> if (progress > 0.12) -0.22 else -0.04
                    TransitionType.DROP -> if (progress > 0.08) -0.12 else -0.03
                }
                val tentativeG = gScore.getValue(current) +
                    moveCost +
                    directnessPenalty +
                    opennessPenalty(level, neighbor) * 0.35 +
                    supportContinuityPenalty(level, snapshot, current, neighbor) * 0.6 +
                    transitionBias -
                    progress * 0.34
                if (tentativeG >= gScore.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    continue
                }

                cameFrom[neighbor] = current
                gScore[neighbor] = tentativeG
                val h = directHeuristic(level, snapshot, neighbor, startCenter, goalCenter)
                openSet += PathStep(neighbor, tentativeG + h, h)
            }
        }

        debug?.log("PF-220", "Direct planner failed after $explored expansions. jumpTransitions=$jumpTransitions descentTransitions=$descentTransitions goal=$goal")
        return null
    }

    private fun directHeuristic(
        level: ClientLevel,
        snapshot: TerrainSnapshot,
        position: BlockPos,
        startCenter: Vec3,
        goalCenter: Vec3
    ): Double {
        val center = nodeCenter(level, snapshot, position)
        val goalDistance = center.distanceTo(goalCenter)
        val linePenalty = pointToSegmentDistance2D(center, startCenter, goalCenter) * 0.40
        val verticalPenalty = abs(center.y - goalCenter.y) * 0.12
        return goalDistance + linePenalty + verticalPenalty
    }

    private fun directDeviationPenalty(
        currentCenter: Vec3,
        neighborCenter: Vec3,
        startCenter: Vec3,
        goalCenter: Vec3
    ): Double {
        val neighborDeviation = pointToSegmentDistance2D(neighborCenter, startCenter, goalCenter)
        val currentDeviation = pointToSegmentDistance2D(currentCenter, startCenter, goalCenter)
        return max(0.0, neighborDeviation - currentDeviation * 0.55) * 0.36
    }

    private fun pointToSegmentDistance2D(point: Vec3, start: Vec3, end: Vec3): Double {
        val segmentX = end.x - start.x
        val segmentZ = end.z - start.z
        val lengthSqr = segmentX * segmentX + segmentZ * segmentZ
        if (lengthSqr <= 1.0e-6) {
            return horizontalDistance(point.subtract(start))
        }
        val t = (((point.x - start.x) * segmentX + (point.z - start.z) * segmentZ) / lengthSqr).coerceIn(0.0, 1.0)
        val projected = Vec3(start.x + segmentX * t, point.y, start.z + segmentZ * t)
        return horizontalDistance(point.subtract(projected))
    }

    private fun buildDescendingFallbackPath(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        start: BlockPos,
        goalNodes: Set<BlockPos>,
        targetAnchor: Vec3,
        searchRadius: Int,
        debug: PathDebugTrace? = null
    ): List<BlockPos>? {
        val route = mutableListOf(start)
        var current = start
        var guard = 0
        while (guard++ < searchRadius * 6) {
            val directGoal = goalNodes.minByOrNull { nodeCenter(level, snapshot, it).distanceTo(nodeCenter(level, snapshot, current)) } ?: return null
            if (current == directGoal) {
                return route
            }
            if (current.y <= directGoal.y) {
                val remainder = findPath(level, player, snapshot, current, goalNodes, searchConfig(current, directGoal), relaxed = true)
                    ?: buildDirectFallbackPath(level, player, snapshot, current, goalNodes, searchRadius, debug)
                    ?: return null
                appendDistinctNodes(route, remainder)
                return route.takeIf { it.lastOrNull() in goalNodes }
            }

            val descentChain = buildCurrentLevelDescentChain(level, player, snapshot, current, goalNodes, targetAnchor, searchRadius)
                ?: return null
            appendDistinctNodes(route, descentChain)
            current = route.last()
        }
        debug?.log("PF-240", "Descending fallback exhausted guard without reaching goal.")
        return route.takeIf { it.lastOrNull() in goalNodes }
    }

    private fun emitPathDebug(source: FabricClientCommandSource, debug: PathDebugTrace, force: Boolean) {
        if (!force && !pathDebugEnabled) {
            return
        }
        debug.lines.forEach { BlazeChat.info(source, it) }
    }

    private fun PathDebugTrace?.fail(code: String, message: String): Nothing? {
        this?.terminalCode = code
        this?.log(code, message)
        return null
    }

    private fun buildCurrentLevelDescentChain(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        start: BlockPos,
        goalNodes: Set<BlockPos>,
        targetAnchor: Vec3,
        searchRadius: Int
    ): List<BlockPos>? {
        val startSupport = supportSurfaceY(level, snapshot, start)
        val openSet = java.util.PriorityQueue<PathStep>(compareBy<PathStep> { it.fScore }.thenBy { it.hScore })
        val cameFrom = HashMap<BlockPos, BlockPos>()
        val gScore = HashMap<BlockPos, Double>()
        val closed = HashSet<BlockPos>()
        openSet += PathStep(start, 0.0, 0.0)
        gScore[start] = 0.0
        var explored = 0
        val localBudget = min(3_000, max(320, searchRadius * 32))

        while (openSet.isNotEmpty() && explored++ < localBudget) {
            val current = openSet.poll().position
            if (!closed.add(current)) {
                continue
            }

            val descentLanding = bestTargetFacingDescentLanding(level, player, snapshot, current, targetAnchor, startSupport)
            if (descentLanding != null) {
                val lipPath = reconstructPath(cameFrom, current)
                return lipPath + descentLanding
            }

            for ((neighbor, moveCost) in neighbors(level, player, snapshot, current, start, searchRadius, relaxed = true)) {
                if (neighbor in closed || !isCurrentLevelTraverse(level, snapshot, startSupport, current, neighbor)) {
                    continue
                }

                val tentativeG = gScore.getValue(current) + moveCost + opennessPenalty(level, neighbor)
                if (tentativeG >= gScore.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    continue
                }

                cameFrom[neighbor] = current
                gScore[neighbor] = tentativeG
                val h = currentLevelHeuristic(level, snapshot, neighbor, goalNodes, targetAnchor, startSupport)
                openSet += PathStep(neighbor, tentativeG + h, h)
            }
        }

        return null
    }

    private fun collectCurrentPlatformEdgeNodes(level: ClientLevel, player: LocalPlayer, origin: BlockPos): List<BlockPos> {
        val capabilities = detectTraversalCapabilities(detectMovementProfile(level, player, player.position()))
        val originSupport = supportSurfaceY(level, origin)
        val queue = java.util.ArrayDeque<BlockPos>()
        val visited = linkedSetOf<BlockPos>()
        queue += origin
        visited += origin

        while (queue.isNotEmpty() && visited.size < EDGE_DEBUG_NODE_LIMIT) {
            val current = queue.removeFirst()
            for (offset in movementOffsets.filter { it.dx == 0 || it.dz == 0 }) {
                val neighbor = findCurrentLevelNeighbor(level, player, current, offset, capabilities, originSupport) ?: continue
                if (visited.add(neighbor)) {
                    queue += neighbor
                }
            }
        }

        return visited.filter { node ->
            movementOffsets.asSequence()
                .filter { it.dx == 0 || it.dz == 0 }
                .any { offset -> isOpenPlatformEdge(level, player, node, offset, capabilities, originSupport, visited) }
        }
    }

    private fun findCurrentLevelNeighbor(
        level: ClientLevel,
        player: LocalPlayer,
        from: BlockPos,
        offset: NodeOffset,
        capabilities: TraversalCapabilities,
        originSupport: Double
    ): BlockPos? {
        val base = from.offset(offset.dx, 0, offset.dz)
        val seen = linkedSetOf<BlockPos>()
        for (yOffset in candidateYOffsets(capabilities)) {
            val candidate = base.offset(0, yOffset, 0)
            if (!seen.add(candidate) || !isStandable(level, player, candidate)) {
                continue
            }
            val candidateSupport = supportSurfaceY(level, candidate)
            if (abs(candidateSupport - originSupport) > EDGE_DEBUG_SUPPORT_TOLERANCE) {
                continue
            }
            val transition = resolveTransition(level, player, captureSingleNodeSnapshot(level, player, from, candidate), from, candidate, relaxed = true)
                ?: continue
            if (transition.type == TransitionType.WALK && transition.clear) {
                return candidate
            }
        }
        return null
    }

    private fun isOpenPlatformEdge(
        level: ClientLevel,
        player: LocalPlayer,
        from: BlockPos,
        offset: NodeOffset,
        capabilities: TraversalCapabilities,
        originSupport: Double,
        platformNodes: Set<BlockPos>
    ): Boolean {
        val sameLevelNeighbor = findCurrentLevelNeighbor(level, player, from, offset, capabilities, originSupport)
        if (sameLevelNeighbor != null && sameLevelNeighbor in platformNodes) {
            return false
        }

        val adjacentFeet = from.offset(offset.dx, 0, offset.dz)
        val adjacentHead = adjacentFeet.above()
        if (!isPassableSpace(level, adjacentFeet, level.getBlockState(adjacentFeet)) ||
            !isPassableSpace(level, adjacentHead, level.getBlockState(adjacentHead))
        ) {
            return false
        }

        val descentCandidate = findDescentCandidate(level, player, from, offset, capabilities)
        if (descentCandidate != null) {
            return true
        }

        val fromCenter = nodeCenter(level, from).add(0.0, 0.05, 0.0)
        val outward = Vec3(offset.dx.toDouble(), 0.0, offset.dz.toDouble())
        val edgeProbe = fromCenter.add(normalize(outward).scale(0.62))
        return level.noCollision(player, playerBox(player, edgeProbe, 0.58))
    }

    private fun findDescentCandidate(
        level: ClientLevel,
        player: LocalPlayer,
        from: BlockPos,
        offset: NodeOffset,
        capabilities: TraversalCapabilities
    ): BlockPos? {
        val snapshot = captureSingleNodeSnapshot(
            level,
            player,
            from,
            from.offset(offset.dx * EDGE_DROP_HORIZONTAL_REACH, 0, offset.dz * EDGE_DROP_HORIZONTAL_REACH)
        )
        return findEdgeDescentCandidates(level, player, snapshot, from, capabilities, preferredOffset = offset)
            .minByOrNull { candidate -> nodeCenter(level, snapshot, candidate.landing).distanceTo(nodeCenter(level, snapshot, from)) }
            ?.landing
    }

    private fun captureSingleNodeSnapshot(
        level: ClientLevel,
        player: LocalPlayer,
        vararg focus: BlockPos
    ): TerrainSnapshot {
        val minX = focus.minOf { it.x } - 2
        val maxX = focus.maxOf { it.x } + 2
        val minY = focus.minOf { it.y } - 8
        val maxY = focus.maxOf { it.y } + 4
        val minZ = focus.minOf { it.z } - 2
        val maxZ = focus.maxOf { it.z } + 2
        val nodes = HashMap<BlockPos, TerrainNodeSnapshot>()
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in minY..maxY) {
                    val position = BlockPos(x, y, z)
                    nodes[position] = TerrainNodeSnapshot(
                        standable = isStandable(level, player, null, position),
                        supportY = supportSurfaceY(level, null, position),
                        supportScore = landingSupportScore(level, null, position)
                    )
                }
            }
        }
        return TerrainSnapshot(minX, maxX, minY, maxY, minZ, maxZ, nodes)
    }

    private fun bestTargetFacingDescentLanding(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        from: BlockPos,
        targetAnchor: Vec3,
        startSupport: Double
    ): BlockPos? {
        val capabilities = detectTraversalCapabilities(detectMovementProfile(level, player, nodeCenter(level, snapshot, from)))
        val fromCenter = nodeCenter(level, snapshot, from)
        val targetVector = normalize(Vec3(targetAnchor.x - fromCenter.x, 0.0, targetAnchor.z - fromCenter.z))
        return findEdgeDescentCandidates(level, player, snapshot, from, capabilities)
            .minByOrNull { candidate ->
                val landingCenter = nodeCenter(level, snapshot, candidate.landing)
                val alignment = if (targetVector == Vec3.ZERO || candidate.direction == Vec3.ZERO) 0.0 else targetVector.dot(candidate.direction)
                val supportDelta = supportSurfaceY(level, snapshot, candidate.landing) - supportSurfaceY(level, snapshot, from)
                val forwardPenalty = if (alignment < -0.1) 1.2 else (1.0 - alignment) * 0.55
                val targetDistance = landingCenter.distanceTo(targetAnchor)
                val supportPenalty = abs(supportSurfaceY(level, snapshot, from) - startSupport) * 0.45
                val dropPenalty = if (candidate.type == TransitionType.DROP) 0.14 else 0.0
                val reachPenalty = candidate.reach * 0.06
                val descentReward = (-supportDelta).coerceAtLeast(0.0) * 0.22
                targetDistance + forwardPenalty + supportPenalty + dropPenalty + reachPenalty - descentReward
            }
            ?.landing
    }

    private fun findEdgeDescentCandidates(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        from: BlockPos,
        capabilities: TraversalCapabilities,
        preferredOffset: NodeOffset? = null
    ): List<EdgeDescentCandidate> {
        val offsets = if (preferredOffset != null) listOf(preferredOffset) else movementOffsets
        val candidates = mutableListOf<EdgeDescentCandidate>()
        for (offset in offsets) {
            val edgeDirection = normalize(Vec3(offset.dx.toDouble(), 0.0, offset.dz.toDouble()))
            if (edgeDirection == Vec3.ZERO) {
                continue
            }
            val adjacentFeet = from.offset(offset.dx, 0, offset.dz)
            val adjacentHead = adjacentFeet.above()
            if (!isPassableSpace(level, adjacentFeet, level.getBlockState(adjacentFeet)) ||
                !isPassableSpace(level, adjacentHead, level.getBlockState(adjacentHead))
            ) {
                continue
            }

            val seen = linkedSetOf<BlockPos>()
            for (reach in 0..EDGE_DROP_HORIZONTAL_REACH) {
                val horizontalBase = from.offset(offset.dx * reach, 0, offset.dz * reach)
                for (drop in 1..capabilities.maxSafeDrop) {
                    val candidate = horizontalBase.below(drop)
                    if (!seen.add(candidate) || !isStandable(level, player, snapshot, candidate)) {
                        continue
                    }

                    val supportDelta = supportSurfaceY(level, snapshot, candidate) - supportSurfaceY(level, snapshot, from)
                    if (supportDelta >= -0.18) {
                        continue
                    }
                    val controlled = supportDelta >= -1.18
                    val transitionType = if (controlled) TransitionType.STEP_DOWN else TransitionType.DROP
                    val landingScore = landingSupportScore(level, snapshot, candidate)
                    if (landingScore < if (controlled) 0.28 else 0.22) {
                        continue
                    }
                    if (!hasEdgeDescentClearance(level, player, snapshot, from, candidate, edgeDirection, controlled)) {
                        continue
                    }
                    candidates += EdgeDescentCandidate(candidate, transitionType, edgeDirection, reach.toDouble())
                }
            }
        }
        return candidates
    }

    private fun hasEdgeDescentClearance(
        level: ClientLevel,
        player: LocalPlayer,
        snapshot: TerrainSnapshot,
        from: BlockPos,
        to: BlockPos,
        edgeDirection: Vec3,
        controlled: Boolean
    ): Boolean {
        val start = descentExecutePoint(level, from, to, edgeDirection).add(0.0, if (controlled) 0.05 else 0.10, 0.0)
        val landing = nodeCenter(level, snapshot, to).add(0.0, if (controlled) 0.06 else 0.08, 0.0)
        val horizontalTravel = horizontalDistance(landing.subtract(start))
        val edgeFall = start
            .add(edgeDirection.scale((0.16 + horizontalTravel * 0.12).coerceIn(0.14, 0.42)))
            .add(0.0, if (controlled) -0.10 else -0.22, 0.0)
        return isPolylineClear(level, player, listOf(start, edgeFall, landing), widthScale = if (controlled) 0.78 else 0.84) ||
            hasRelaxedLineClearance(level, player, start, landing, if (controlled) 0.74 else 0.80)
    }

    private fun isCurrentLevelTraverse(
        level: ClientLevel,
        snapshot: TerrainSnapshot,
        startSupport: Double,
        from: BlockPos,
        to: BlockPos
    ): Boolean {
        val supportDelta = supportSurfaceY(level, snapshot, to) - supportSurfaceY(level, snapshot, from)
        val absoluteDelta = abs(supportSurfaceY(level, snapshot, to) - startSupport)
        return supportDelta >= -0.24 && supportDelta <= 0.58 && absoluteDelta <= 0.85
    }

    private fun currentLevelHeuristic(
        level: ClientLevel,
        snapshot: TerrainSnapshot,
        position: BlockPos,
        goalNodes: Set<BlockPos>,
        targetAnchor: Vec3,
        startSupport: Double
    ): Double {
        val center = nodeCenter(level, snapshot, position)
        val goalDistance = goalNodes.minOfOrNull { nodeCenter(level, snapshot, it).distanceTo(center) } ?: center.distanceTo(targetAnchor)
        val targetDistance = center.distanceTo(targetAnchor)
        val supportPenalty = abs(supportSurfaceY(level, snapshot, position) - startSupport) * 0.45
        return goalDistance * 0.42 + targetDistance * 0.68 + supportPenalty
    }

    private fun appendDistinctNodes(target: MutableList<BlockPos>, additions: List<BlockPos>) {
        additions.forEach { node ->
            if (target.lastOrNull() != node) {
                target += node
            }
        }
    }

    private fun terrainPenalty(level: ClientLevel, position: BlockPos): Double {
        val floorState = level.getBlockState(position.below())
        val block = floorState.block
        val speedFactor = block.getSpeedFactor().toDouble()
        val jumpFactor = block.getJumpFactor().toDouble()
        val friction = block.getFriction().toDouble()
        var penalty = 0.0
        if (speedFactor < 1.0) {
            penalty += (1.0 - speedFactor) * 2.6
        }
        if (jumpFactor < 1.0) {
            penalty += (1.0 - jumpFactor) * 1.4
        }
        if (friction < 0.72) {
            penalty += (0.72 - friction) * 2.2
        } else if (friction > 0.96) {
            penalty += (friction - 0.96) * 1.8
        }
        if (isSmoothWalkSurface(level, position.below(), floorState)) {
            penalty -= 0.12
        }
        return penalty
    }

    private fun supportContinuityPenalty(level: ClientLevel, snapshot: TerrainSnapshot?, from: BlockPos, to: BlockPos): Double {
        val fromFloor = level.getBlockState(from.below())
        val toFloor = level.getBlockState(to.below())
        val fromSupport = landingSupportScore(level, snapshot, from)
        val toSupport = landingSupportScore(level, snapshot, to)
        val supportPenalty = max(0.0, fromSupport - toSupport) * 0.22
        val frictionPenalty = max(0.0, fromFloor.block.getFriction().toDouble() - toFloor.block.getFriction().toDouble()) * 0.08
        return supportPenalty + frictionPenalty
    }

    private fun headingFlowPenalty(level: ClientLevel, snapshot: TerrainSnapshot?, previous: BlockPos?, current: BlockPos, next: BlockPos): Double {
        previous ?: return 0.0
        val inVector = nodeCenter(level, snapshot, current).subtract(nodeCenter(level, snapshot, previous))
        val outVector = nodeCenter(level, snapshot, next).subtract(nodeCenter(level, snapshot, current))
        if (inVector.lengthSqr() <= 1.0e-6 || outVector.lengthSqr() <= 1.0e-6) {
            return 0.0
        }
        val turnAngle = abs(Mth.wrapDegrees(desiredYawTo(Vec3.ZERO, inVector) - desiredYawTo(Vec3.ZERO, outVector)))
        return (turnAngle / 180.0) * 0.18
    }

    private fun opennessPenalty(level: ClientLevel, position: BlockPos): Double {
        var penalty = 0.0
        for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val feet = position.offset(dx, 0, dz)
            val head = position.offset(dx, 1, dz)
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty) {
                penalty += 0.10
            }
            if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty) {
                penalty += 0.08
            }
        }
        return penalty
    }

    private fun goalProgressBias(level: ClientLevel, snapshot: TerrainSnapshot?, current: BlockPos, candidate: BlockPos, goalCenters: List<Vec3>): Double {
        val currentCenter = nodeCenter(level, snapshot, current)
        val candidateCenter = nodeCenter(level, snapshot, candidate)
        val currentHorizontal = goalCenters.minOfOrNull { goal -> horizontalDistance(currentCenter.subtract(goal)) } ?: 0.0
        val candidateHorizontal = goalCenters.minOfOrNull { goal -> horizontalDistance(candidateCenter.subtract(goal)) } ?: 0.0
        val progress = (currentHorizontal - candidateHorizontal).coerceAtLeast(0.0)
        val supportDelta = supportSurfaceY(level, snapshot, candidate) - supportSurfaceY(level, snapshot, current)
        val jumpBonus = when {
            supportDelta > 0.42 && progress > 0.35 -> progress * 0.30 + min(0.22, supportDelta * 0.10)
            supportDelta < -1.18 && progress > 0.35 -> progress * 0.16
            supportDelta < -0.34 && progress > 0.20 -> progress * 0.24 + min(0.18, -supportDelta * 0.08)
            else -> progress * 0.08
        }
        return -jumpBonus
    }

    private fun traversalCost(level: ClientLevel, snapshot: TerrainSnapshot?, from: BlockPos, to: BlockPos, baseCost: Double, transition: TransitionType): Double {
        val supportDelta = supportSurfaceY(level, snapshot, to) - supportSurfaceY(level, snapshot, from)
        val climb = max(0.0, supportDelta)
        val drop = max(0.0, -supportDelta)
        val transitionPenalty = when (transition) {
            TransitionType.WALK -> 0.03
            TransitionType.STEP_DOWN -> -0.04
            TransitionType.JUMP -> (-0.05 - learning.jumpBias * 0.08).coerceAtLeast(-0.14)
            TransitionType.DROP -> (0.01 - learning.dropBias * 0.10).coerceAtLeast(-0.06)
        }
        return baseCost +
            climb * (0.10 - learning.jumpBias * 0.12).coerceAtLeast(0.015) +
            drop * when (transition) {
                TransitionType.STEP_DOWN -> 0.0
                else -> (0.03 - learning.dropBias * 0.08).coerceAtLeast(0.0)
            } +
            transitionPenalty
    }

    private fun heuristic(level: ClientLevel, snapshot: TerrainSnapshot?, position: BlockPos, goalCenters: List<Vec3>): Double {
        val center = nodeCenter(level, snapshot, position)
        return goalCenters.minOf { goal ->
            val dx = center.x - goal.x
            val dz = center.z - goal.z
            val horizontal = sqrt(dx * dx + dz * dz)
            val elevation = center.y - goal.y
            max(
                horizontal,
                max(0.0, -elevation) * 1.15 + max(0.0, elevation) * 0.55
            )
        }
    }

    private fun playerBox(player: LocalPlayer, feetCenter: Vec3, widthScale: Double = 1.0): AABB {
        val halfWidth = player.bbWidth.toDouble() * widthScale / 2.0
        return AABB(
            feetCenter.x - halfWidth,
            feetCenter.y,
            feetCenter.z - halfWidth,
            feetCenter.x + halfWidth,
            feetCenter.y + player.bbHeight.toDouble(),
            feetCenter.z + halfWidth
        )
    }

    private fun reconstructPath(cameFrom: Map<BlockPos, BlockPos>, end: BlockPos): List<BlockPos> {
        val reversedPath = mutableListOf(end)
        var cursor = end
        while (true) {
            val previous = cameFrom[cursor] ?: break
            reversedPath += previous
            cursor = previous
        }
        reversedPath.reverse()
        return reversedPath
    }

    private fun nodeCenter(level: ClientLevel, position: BlockPos): Vec3 {
        return nodeCenter(level, null, position)
    }

    private fun nodeCenter(level: ClientLevel, snapshot: TerrainSnapshot?, position: BlockPos): Vec3 {
        return Vec3(position.x + 0.5, supportSurfaceY(level, snapshot, position), position.z + 0.5)
    }

    private fun nodeCenter(position: BlockPos): Vec3 {
        return Vec3(position.x + 0.5, position.y.toDouble(), position.z + 0.5)
    }

    private fun supportSurfaceY(level: ClientLevel, standableNode: BlockPos): Double {
        return supportSurfaceY(level, null, standableNode)
    }

    private fun supportSurfaceY(level: ClientLevel, snapshot: TerrainSnapshot?, standableNode: BlockPos): Double {
        snapshot?.node(standableNode)?.let { return it.supportY }
        val floorPos = standableNode.below()
        val floorShape = level.getBlockState(floorPos).getCollisionShape(level, floorPos)
        val localTop = if (floorShape.isEmpty) 1.0 else floorShape.bounds().maxY.coerceIn(0.0, 1.0)
        return floorPos.y + localTop
    }

    private fun pathfinderConfig(): BlazePathfinderConfig {
        return BlazeDataStore.state.pathfinder ?: BlazePathfinderConfig()
    }

    private fun normalize(vector: Vec3): Vec3 {
        val length = vector.length()
        return if (length <= 1.0e-5) Vec3.ZERO else vector.scale(1.0 / length)
    }

    private fun horizontalDistance(delta: Vec3): Double {
        return sqrt(delta.x * delta.x + delta.z * delta.z)
    }

    private data class SearchConfig(
        val horizontalRadius: Int,
        val maxNodes: Int
    )

    private data class TerrainSnapshot(
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val minZ: Int,
        val maxZ: Int,
        val nodes: Map<BlockPos, TerrainNodeSnapshot>
    ) {
        fun node(position: BlockPos): TerrainNodeSnapshot? = nodes[position]
    }

    private data class TerrainNodeSnapshot(
        val standable: Boolean,
        val supportY: Double,
        val supportScore: Double
    )

    private data class RoutePlan(
        val pathNodes: List<BlockPos>,
        val samples: List<Vec3>,
        val goalPoint: Vec3,
        val renderBoxes: List<Pair<AABB, WorldBoxRenderer.BoxStyle>>,
        val renderQuads: List<WorldBoxRenderer.SurfaceQuad>,
        val actions: List<RouteAction>,
        val jumpLaunchNodes: Set<BlockPos>,
        val totalLength: Double
    )

    private data class PathSession(
        val targetId: Int?,
        val staticTarget: BlockPos?,
        val exactGoal: Boolean,
        val arrivalDistance: Double,
        val targetLabel: String,
        val route: RoutePlan,
        val routeCursor: Int,
        val lastTargetPosition: Vec3,
        val lastPlayerPosition: Vec3,
        val lastRemainingDistance: Double,
        val stuckTicks: Int,
        val recoveryAttempts: Int,
        val repathTicks: Int,
        val actionIndex: Int,
        val actionPhase: ActionPhase,
        val actionPhaseTicks: Int,
        val rotationState: RotationControllerState
    )

    private data class RouteProjection(
        val segmentIndex: Int,
        val point: Vec3,
        val tangent: Vec3,
        val distanceToRoute: Double,
        val remainingDistance: Double
    )

    private data class ActionExecutionState(
        val action: RouteAction?,
        val index: Int,
        val phase: ActionPhase,
        val phaseTicks: Int
    )

    private data class RouteAction(
        val type: RouteActionType,
        val executePoint: Vec3,
        val landingPoint: Vec3,
        val aimPoint: Vec3,
        val segmentIndex: Int,
        val entryTangent: Vec3,
        val exitTangent: Vec3,
        val commitmentDistance: Double,
        val completionProgress: Double,
        val lateralTolerance: Double,
        val expectedSupportY: Double,
        val minimumRequiredSpeed: Double,
        val preferredSpeed: Double,
        val headClearance: Double
    )

    private data class RotationControllerState(
        val smoothedLookTarget: Vec3? = null,
        val smoothedMoveTarget: Vec3? = null,
        val filteredViewYaw: Float? = null,
        val filteredMoveYaw: Float? = null,
        val filteredYawTarget: Float? = null,
        val filteredPitch: Float? = null,
        val rotationCurve: RotationCurve? = null,
        val bodyYawTarget: Float? = null,
        val headYawTarget: Float? = null,
        val headFreedom: Float? = null
    )

    private data class RotationCurve(
        val startYaw: Float,
        val startPitch: Float,
        val controlYaw: Float,
        val controlPitch: Float,
        val endYaw: Float,
        val endPitch: Float,
        val startNanos: Long,
        val durationNanos: Long
    ) {
        fun progressAt(now: Long): Float {
            if (durationNanos <= 0L) return 1.0f
            return ((now - startNanos).toDouble() / durationNanos.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
        }
    }

    private data class TurnPreview(
        val segmentIndex: Int,
        val distance: Double,
        val angle: Float,
        val turnPoint: Vec3,
        val entryTangent: Vec3,
        val exitTangent: Vec3,
        val exitPoint: Vec3
    )

    private enum class RouteActionType {
        WALK,
        STEP_DOWN,
        JUMP,
        DROP
    }

    private enum class ActionPhase {
        NONE,
        ALIGN,
        APPROACH,
        COMMIT,
        TRAVERSE,
        RECOVERY
    }

    private data class MovementProfile(
        val horizontalSpeed: Double,
        val speedLevel: Int,
        val jumpLevel: Int,
        val movementSpeedModifier: Double,
        val lookaheadDistance: Double,
        val jumpLookaheadDistance: Double,
        val recoveryRadius: Double,
        val targetCruiseSpeed: Double,
        val forwardYawLimit: Float,
        val strafeYawLimit: Float,
        val sprintYawLimit: Float,
        val strafeDeadband: Float,
        val jumpAssistHeight: Double,
        val jumpImpulse: Double,
        val jumpLaunchLeadDistance: Double,
        val jumpRunupDistance: Double
    )

    private data class TargetSnapshot(
        val anchor: Vec3,
        val blockPosition: BlockPos
    )

    private data class TraversalCapabilities(
        val maxJumpUp: Int,
        val maxSafeDrop: Int,
        val maxJumpHeight: Double,
        val jumpArcHeight: Double,
        val jumpImpulse: Double,
        val preferredGroundSpeed: Double,
        val preferredAirSpeed: Double
    )

    private data class TransitionFeasibility(
        val clear: Boolean,
        val landingScore: Double,
        val minimumHeadroom: Double,
        val minimumRequiredSpeed: Double
    )

    private data class EdgeDescentCandidate(
        val landing: BlockPos,
        val type: TransitionType,
        val direction: Vec3,
        val reach: Double
    )

    private data class PathDebugTrace(
        val lines: MutableList<String> = mutableListOf(),
        var terminalCode: String? = null
    ) {
        fun log(code: String, message: String) {
            lines += "[$code] $message"
        }
    }

    private data class PathStep(
        val position: BlockPos,
        val fScore: Double,
        val hScore: Double
    )

    private data class NodeOffset(
        val dx: Int,
        val dz: Int,
        val baseCost: Double
    )

    private data class TransitionCheck(
        val type: TransitionType,
        val clear: Boolean
    )

    private enum class TransitionType {
        WALK,
        STEP_DOWN,
        JUMP,
        DROP
    }

    private data class LearningState(
        val jumpBias: Double = 0.0,
        val searchRadiusBonus: Int = 0,
        val verticalSearchBonus: Int = 0,
        val stuckThreshold: Int = 8,
        val lookaheadBonus: Double = 0.0,
        val turnBonus: Float = 0.0f,
        val dropBias: Double = 0.0
    ) {
        fun observeFailure(): LearningState = copy(
            jumpBias = (jumpBias + 0.12).coerceAtMost(0.9),
            searchRadiusBonus = (searchRadiusBonus + 18).coerceAtMost(160),
            verticalSearchBonus = (verticalSearchBonus + 1).coerceAtMost(6),
            stuckThreshold = (stuckThreshold - 1).coerceAtLeast(4),
            lookaheadBonus = (lookaheadBonus + 0.22).coerceAtMost(1.0),
            turnBonus = (turnBonus + 0.05f).coerceAtMost(0.12f),
            dropBias = (dropBias + 0.08).coerceAtMost(0.45)
        )

        fun observeProgress(progressDelta: Double): LearningState {
            if (progressDelta <= 0.015) {
                return this
            }
            return copy(
                jumpBias = (jumpBias - 0.02).coerceAtLeast(0.0),
                searchRadiusBonus = (searchRadiusBonus - 3).coerceAtLeast(0),
                verticalSearchBonus = (verticalSearchBonus - 1).coerceAtLeast(0),
                stuckThreshold = (stuckThreshold + 1).coerceAtMost(8),
                lookaheadBonus = (lookaheadBonus - 0.05).coerceAtLeast(0.0),
                turnBonus = (turnBonus - 0.01f).coerceAtLeast(0.0f),
                dropBias = (dropBias - 0.03).coerceAtLeast(0.0)
            )
        }

        fun observeSuccess(): LearningState = copy(
            jumpBias = (jumpBias - 0.08).coerceAtLeast(0.0),
            searchRadiusBonus = (searchRadiusBonus - 8).coerceAtLeast(0),
            verticalSearchBonus = (verticalSearchBonus - 1).coerceAtLeast(0),
            stuckThreshold = (stuckThreshold + 1).coerceAtMost(9),
            lookaheadBonus = (lookaheadBonus - 0.12).coerceAtLeast(0.0),
            turnBonus = (turnBonus - 0.03f).coerceAtLeast(0.0f),
            dropBias = (dropBias - 0.08).coerceAtLeast(0.0)
        )
    }

    private fun detectTraversalCapabilities(profile: MovementProfile): TraversalCapabilities {
        val jumpHeight = profile.jumpAssistHeight
        val maxJumpUp = when {
            jumpHeight >= 3.15 -> 4
            jumpHeight >= 2.35 -> 3
            jumpHeight >= 1.60 -> 2
            else -> 1
        }
        val maxSafeDrop = (4 + (if (profile.jumpLevel > 0) 1 else 0) + learning.verticalSearchBonus / 2).coerceIn(4, 8)
        val jumpArcHeight = (jumpHeight * 0.72 + learning.jumpBias * 0.16).coerceIn(0.92, 3.8)
        val jumpImpulse = profile.jumpImpulse.coerceIn(0.42, 1.28)
        val preferredGroundSpeed = max(profile.targetCruiseSpeed, 0.16).coerceIn(0.16, 1.25)
        val preferredAirSpeed = (
            preferredGroundSpeed +
                0.03 +
                max(0.0, profile.movementSpeedModifier - 1.0) * 0.018
            ).coerceIn(0.18, 1.32)
        return TraversalCapabilities(maxJumpUp, maxSafeDrop, jumpHeight, jumpArcHeight, jumpImpulse, preferredGroundSpeed, preferredAirSpeed)
    }

    private fun documentedJumpHeight(jumpLevel: Int): Double {
        return when (jumpLevel) {
            0 -> 1.2522
            1 -> 1.8361
            2 -> 2.5168
            else -> 2.5168 + (jumpLevel - 2) * 0.72
        }
    }

    private fun routeLength(samples: List<Vec3>): Double {
        var total = 0.0
        for (index in 0 until samples.lastIndex) {
            total += samples[index].distanceTo(samples[index + 1])
        }
        return total
    }
}
