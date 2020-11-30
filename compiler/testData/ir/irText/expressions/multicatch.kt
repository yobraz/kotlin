class MultiCatchTest {
    fun test1() {
        try {

        } catch (e: IllegalArgumentException, NullPointerException) {

        }
    }

    fun test2() {
        try {

        } catch (e: IllegalArgumentException, NullPointerException) {

        } finally {

        }
    }

    fun test3() {
        try {

        } catch (e: IllegalArgumentException, NullPointerException, IllegalStateException, ) {

        }
    }

    fun test4() {
        try {

            try {

            } catch (f: IllegalStateException, NullPointerException) {

            } finally {

            }
        } catch (e: IllegalArgumentException, NullPointerException) {

        }
    }
}