class Test<T : <!UNION_TYPE_PROHIBITED_POSITION!>Int | String<!>>(val x: T)

fun <T : <!UNION_TYPE_PROHIBITED_POSITION!>Int | String<!>, V> test() {}
