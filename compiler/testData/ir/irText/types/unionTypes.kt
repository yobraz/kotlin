fun getSize(x: Int | String | Boolean): Int {
    return when (x) {
        is Int -> x
        is String -> (x).length
        is Boolean -> 1
    }
}

fun getRandomObject(): Int | String | Boolean {
    return if (java.util.Random().nextBoolean())
        if (java.util.Random().nextBoolean())
            true
        else
            7
    else
        "fff"
}

fun first() {
    val obj = getRandomObject()
    getSize(obj)
//    val x: Int | String = 5
//
    try {

    } catch (e: java.lang.IllegalStateException | java.lang.NullPointerException) {

    }
//    return x
//
//
//    val e: Int = when (x) {
//        is Int -> x
//        is String -> x.length
//    }
//
//    System.out.println(x)
//
//    x = "fr"
//
//    System.out.println(x)
//
//    val z = ArrayList<Int | Boolean>()
//    z.add(5)
//    z.add(true)
//
//    System.out.println(z as ArrayList<Int | Boolean>)
}
