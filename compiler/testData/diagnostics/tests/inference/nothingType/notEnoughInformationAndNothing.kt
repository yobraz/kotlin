// FILE: OCNewFileActionBase.java
public class OCNewFileActionBase<T extends OCNewFileActionBase<T>.CreateFileDialogBase> {
    public class CreateFileDialogBase { }

    static OCNewFileActionBase get() { return new OCNewFileActionBase(); }
}

// FILE: main.kt
fun main() {
    // Before changes in raw types computation: (OCNewFileActionBase<OCNewFileActionBase<*>.CreateFileDialogBase!>..OCNewFileActionBase<out OCNewFileActionBase<*>.CreateFileDialogBase!>?)
    // After that: raw (OCNewFileActionBase<*>..OCNewFileActionBase<*>?)
    val x = <!DEBUG_INFO_EXPRESSION_TYPE("raw (OCNewFileActionBase<*>..OCNewFileActionBase<*>?)")!>OCNewFileActionBase.get()<!>
}