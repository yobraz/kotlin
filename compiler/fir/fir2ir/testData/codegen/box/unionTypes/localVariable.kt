fun box(): String {
    var x: Int | String = 5
    x.hashCode()
    x = "OK"

    if (x is String)
        return x

    return "Fail"
}