fun expressionTree(block: () -> Unit) {
    TODO()
}

val f = expressionTree {
    if (uCullMode == 0.0) 1.0
    else if (fFaceDirection(position, fNormal()) * uCullMode >= 0.0) 1.0
    else 0.0
}