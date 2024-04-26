package io.morfly.compose.bottomsheet.material3

import androidx.annotation.IntRange
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@ExperimentalMaterial3Api
@ExperimentalFoundationApi
@Composable
fun <T : Any> rememberAnchoredDraggableState(
    initialValue: T,
    positionalThreshold: (totalDistance: Float) -> Float = BottomSheetDefaults.PositionalThreshold,
    velocityThreshold: () -> Float = BottomSheetDefaults.VelocityThreshold,
    animationSpec: AnimationSpec<Float> = BottomSheetDefaults.AnimationSpec,
    confirmValueChange: (newValue: T) -> Boolean = { true }
) = rememberSaveable(
    positionalThreshold, velocityThreshold, animationSpec, confirmValueChange,
    saver = AnchoredDraggableState.Saver(
        animationSpec = animationSpec,
        positionalThreshold = positionalThreshold,
        velocityThreshold = velocityThreshold,
        confirmValueChange = confirmValueChange
    )
) {
    AnchoredDraggableState(
        initialValue = initialValue,
        positionalThreshold = positionalThreshold,
        velocityThreshold = velocityThreshold,
        animationSpec = animationSpec,
        confirmValueChange = confirmValueChange
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : Any> rememberBottomSheetState(
    draggableState: AnchoredDraggableState<T>,
    defineValues: BottomSheetValuesConfig<T>.() -> Unit,
) = remember(draggableState, defineValues) {
    BottomSheetState(draggableState, defineValues)
}

@ExperimentalMaterial3Api
@ExperimentalFoundationApi
@Composable
fun <T : Any> rememberBottomSheetState(
    initialValue: T,
    defineValues: BottomSheetValuesConfig<T>.() -> Unit,
    positionalThreshold: (totalDistance: Float) -> Float = BottomSheetDefaults.PositionalThreshold,
    velocityThreshold: () -> Float = BottomSheetDefaults.VelocityThreshold,
    animationSpec: AnimationSpec<Float> = BottomSheetDefaults.AnimationSpec,
    confirmValueChange: BottomSheetState<T>.(newValue: T) -> Boolean = { true }
): BottomSheetState<T> {
    lateinit var state: BottomSheetState<T>
    lateinit var draggableState: AnchoredDraggableState<T>

    val scope = rememberCoroutineScope()
    var prevOffset by remember { mutableFloatStateOf(0f) }

    draggableState = rememberAnchoredDraggableState(
        initialValue = initialValue,
        positionalThreshold = positionalThreshold,
        velocityThreshold = velocityThreshold,
        animationSpec = animationSpec,
        confirmValueChange = { value ->
            with(draggableState) {
                val currentOffset = requireOffset()
                val searchUpwards =
                    if (prevOffset == currentOffset) null
                    else prevOffset < currentOffset

                prevOffset = currentOffset
                if (!anchors.hasAnchorFor(value)) {
                    val closest = if (searchUpwards != null) {
                        anchors.closestAnchor(currentOffset, searchUpwards)
                    } else {
                        anchors.closestAnchor(currentOffset)
                    }
                    if (closest != null) {
                        scope.launch { animateTo(closest) }
                    }
                    false
                } else {
                    state.confirmValueChange(value)
                }
            }
        }
    )

    state = rememberBottomSheetState(draggableState, defineValues)
    return state
}

@ExperimentalMaterial3Api
@ExperimentalFoundationApi
@Composable
fun <T : Any> BottomSheetScaffold(
    sheetState: BottomSheetState<T>,
    sheetContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetShape: Shape = BottomSheetDefaults.ExpandedShape,
    sheetContainerColor: Color = BottomSheetDefaults.ContainerColor,
    sheetContentColor: Color = contentColorFor(sheetContainerColor),
    sheetTonalElevation: Dp = BottomSheetDefaults.Elevation,
    sheetShadowElevation: Dp = BottomSheetDefaults.Elevation,
    sheetDragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    sheetSwipeEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    onSheetMoved: ((sheetHeight: Dp) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current

    BottomSheetScaffoldLayout(
        modifier = modifier,
        body = content,
        sheetOffset = { sheetState.draggableState.requireOffset() },
        containerColor = containerColor,
        contentColor = contentColor,
        bottomSheet = { layoutHeight ->
            BottomSheet(
                state = sheetState,
                sheetMaxWidth = sheetMaxWidth,
                sheetSwipeEnabled = sheetSwipeEnabled,
                calculateAnchors = { sheetSize ->
                    val config = BottomSheetValuesConfig<T>(
                        layoutHeight = layoutHeight,
                        sheetHeight = sheetSize.height,
                        density = density,
                    )
                    sheetState.defineValues(config)
                    require(config.values.isNotEmpty()) { "No bottom sheet values provided!" }

                    DraggableAnchors {
                        for ((state, value) in config.values) {
                            state at value
                        }
                    }
                },
                onMoved = if (onSheetMoved != null) { offset ->
                    val padding = layoutHeight - offset.y
                    val paddingDp = with(density) { padding.toDp() }
                    onSheetMoved(paddingDp)
                } else {
                    null
                },
                shape = sheetShape,
                containerColor = sheetContainerColor,
                contentColor = sheetContentColor,
                tonalElevation = sheetTonalElevation,
                shadowElevation = sheetShadowElevation,
                dragHandle = sheetDragHandle,
                content = sheetContent
            )
        },
    )
}

@Composable
internal fun BottomSheetScaffoldLayout(
    modifier: Modifier,
    body: @Composable (innerPadding: PaddingValues) -> Unit,
    bottomSheet: @Composable (layoutHeight: Int) -> Unit,
    sheetOffset: () -> Float,
    containerColor: Color,
    contentColor: Color,
) {
    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        val sheetPlaceable = subcompose(BottomSheetScaffoldLayoutSlot.Sheet) {
            bottomSheet(layoutHeight)
        }[0].measure(constraints)

        val bodyPlaceable = subcompose(BottomSheetScaffoldLayoutSlot.Body) {
            Surface(
                modifier = modifier,
                color = containerColor,
                contentColor = contentColor,
            ) { body(PaddingValues()) }
        }[0].measure(constraints)

        layout(width = layoutWidth, height = layoutHeight) {
            val sheetOffsetY = sheetOffset().roundToInt()
            val sheetOffsetX = Integer.max(0, (layoutWidth - sheetPlaceable.width) / 2)

            bodyPlaceable.placeRelative(x = 0, y = 0)
            sheetPlaceable.placeRelative(sheetOffsetX, sheetOffsetY)
        }
    }
}

private enum class BottomSheetScaffoldLayoutSlot { Body, Sheet }

@ExperimentalFoundationApi
@Composable
internal fun <T : Any> BottomSheet(
    state: BottomSheetState<T>,
    calculateAnchors: (sheetSize: IntSize) -> DraggableAnchors<T>,
    onMoved: ((sheetOffset: Offset) -> Unit)?,
    sheetMaxWidth: Dp,
    sheetSwipeEnabled: Boolean,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    tonalElevation: Dp,
    shadowElevation: Dp,
    dragHandle: @Composable (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    val draggableState = state.draggableState
    val scope = rememberCoroutineScope()
    val orientation = Orientation.Vertical

    DisposableEffect(state) {
        val onValuesRequested = fun(layoutSize: IntSize) {
            val newAnchors = calculateAnchors(layoutSize)
            if (draggableState.offset.isNaN()) {
                draggableState.updateAnchors(newAnchors, draggableState.targetValue)
            } else scope.launch {
                draggableState.updateAnchorsAnimated(newAnchors, draggableState.targetValue)
            }
        }
        state.onValuesRequested += onValuesRequested
        onDispose {
            state.onValuesRequested -= onValuesRequested
        }
    }

    Surface(
        modifier = Modifier
            .widthIn(max = sheetMaxWidth)
            .fillMaxWidth()
            .nestedScroll(
                remember(state) {
                    BottomSheetNestedScrollConnection(
                        anchoredDraggableState = draggableState,
                        orientation = orientation,
                        onFling = { velocity ->
                            scope.launch { draggableState.settle(velocity) }
                        }
                    )
                },
            )
            .anchoredDraggable(
                state = draggableState,
                orientation = orientation,
                enabled = sheetSwipeEnabled,
            )
            .onGloballyPositioned { coordinates ->
                if (onMoved != null) {
                    val offset = coordinates.positionInParent()
                    if (offset != state.sheetOffset) {
                        state.sheetOffset = offset
                        onMoved(offset)
                    }
                }
            }
            .onSizeChanged { layoutSize ->
                state.layoutSize = layoutSize
                val newAnchors = calculateAnchors(layoutSize)
                draggableState.updateAnchors(newAnchors, draggableState.targetValue)
            },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (dragHandle != null) {
                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    dragHandle()
                }
            }
            content()
        }
    }
}

@ExperimentalFoundationApi
@Suppress("FunctionName")
internal fun <T> BottomSheetNestedScrollConnection(
    anchoredDraggableState: AnchoredDraggableState<T>,
    orientation: Orientation,
    onFling: (velocity: Float) -> Unit,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.toFloat()
        return if (delta < 0 && source == NestedScrollSource.Drag) {
            anchoredDraggableState.dispatchRawDelta(delta).toOffset()
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return if (source == NestedScrollSource.Drag) {
            anchoredDraggableState.dispatchRawDelta(available.toFloat()).toOffset()
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val toFling = available.toFloat()
        val currentOffset = anchoredDraggableState.requireOffset()
        val minAnchor = anchoredDraggableState.anchors.minAnchor()
        return if (toFling < 0 && currentOffset > minAnchor) {
            onFling(toFling)
            // since we go to the anchor with tween settling, consume all for the best UX
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        onFling(available.toFloat())
        return available
    }

    private fun Float.toOffset(): Offset = Offset(
        x = if (orientation == Orientation.Horizontal) this else 0f,
        y = if (orientation == Orientation.Vertical) this else 0f,
    )

    @JvmName("velocityToFloat")
    private fun Velocity.toFloat() = if (orientation == Orientation.Horizontal) x else y

    @JvmName("offsetToFloat")
    private fun Offset.toFloat(): Float = if (orientation == Orientation.Horizontal) x else y
}

class BottomSheetValuesConfig<T : Any>(
    val layoutHeight: Int,
    val sheetHeight: Int,
    val density: Density,
) {
    val contentHeight = Value((layoutHeight - sheetHeight).toFloat())

    val values = mutableMapOf<T, Float>()

    infix fun T.at(value: Value) {
        values[this] = maxOf(value.offsetPx, 0f)
    }

    fun offset(px: Float): Value {
        return Value(px)
    }

    fun offset(offset: Dp): Value {
        return Value(with(density) { offset.toPx() })
    }

    fun offset(@IntRange(from = 0, to = 100) percent: Int): Value {
        return Value(layoutHeight * percent / 100f)
    }

    fun height(px: Float): Value {
        return Value(layoutHeight - offset(px).offsetPx)
    }

    fun height(height: Dp): Value {
        return Value(layoutHeight - offset(height).offsetPx)
    }

    fun height(@IntRange(from = 0, to = 100) percent: Int): Value {
        return Value(layoutHeight - offset(percent).offsetPx)
    }

    @JvmInline
    value class Value internal constructor(val offsetPx: Float)
}