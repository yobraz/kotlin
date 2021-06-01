
fun ubyte(vararg a: <!EXPERIMENTAL_API_USAGE!>UByte<!>): <!EXPERIMENTAL_API_USAGE!>UByteArray<!> = a
fun ushort(vararg a: <!EXPERIMENTAL_API_USAGE!>UShort<!>): <!EXPERIMENTAL_API_USAGE!>UShortArray<!> = a
fun uint(vararg a: <!EXPERIMENTAL_API_USAGE!>UInt<!>): <!EXPERIMENTAL_API_USAGE!>UIntArray<!> = a
fun ulong(vararg a: <!EXPERIMENTAL_API_USAGE!>ULong<!>): <!EXPERIMENTAL_API_USAGE!>ULongArray<!> = a

fun rawUInt(vararg a: <!EXPERIMENTAL_API_USAGE!>UInt<!>): IntArray = <!RETURN_TYPE_MISMATCH!>a<!>
