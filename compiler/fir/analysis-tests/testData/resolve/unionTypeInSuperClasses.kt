interface A
interface B
open class C

class D : <!UNION_TYPE_PROHIBITED_POSITION!>A | B<!>, C()
