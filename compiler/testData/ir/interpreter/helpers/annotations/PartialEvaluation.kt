package kotlin

/**
 * Specify that marked function can be optimized using compile time evaluationss
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class PartialEvaluation