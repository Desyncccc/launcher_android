package com.liquid.launcher.glass

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalGraphicsContext

/**
 * A live capture of some part of the UI that glass elements can refract.
 * The capture is a RenderNode (GraphicsLayer); glass elements draw it *by
 * reference*, so they always see the latest frame without re-recording.
 */
@Stable
class Backdrop(val name: String) {
    internal var layer: GraphicsLayer? by mutableStateOf(null)
    internal var positionInRoot: Offset = Offset.Zero

    /** Average luminance (0..1) of the content under [rectInRoot]; drives adaptivity. */
    var luminance: ((Rect) -> Float)? = null
}

/** Records everything this node draws into [backdrop] (and still draws it). */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this then BackdropSourceElement(backdrop)

private data class BackdropSourceElement(val backdrop: Backdrop) : ModifierNodeElement<BackdropSourceNode>() {
    override fun create() = BackdropSourceNode(backdrop)
    override fun update(node: BackdropSourceNode) {
        if (node.backdrop !== backdrop) {
            node.backdrop.layer = null
            node.backdrop = backdrop
            backdrop.layer = node.layer
        }
    }
    override fun InspectorInfo.inspectableProperties() { name = "backdropSource" }
}

private class BackdropSourceNode(var backdrop: Backdrop) : Modifier.Node(), DrawModifierNode,
    GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    var layer: GraphicsLayer? = null
    private var graphicsContext: GraphicsContext? = null

    override fun onAttach() {
        val ctx = currentValueOf(LocalGraphicsContext)
        graphicsContext = ctx
        layer = ctx.createGraphicsLayer().also { backdrop.layer = it }
    }

    override fun onDetach() {
        layer?.let { graphicsContext?.releaseGraphicsLayer(it) }
        if (backdrop.layer === layer) backdrop.layer = null
        layer = null
    }

    override fun ContentDrawScope.draw() {
        val l = layer
        if (l == null) {
            drawContent(); return
        }
        l.record { this@draw.drawContent() }
        drawLayer(l)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        backdrop.positionInRoot = coordinates.positionInRoot()
    }
}
