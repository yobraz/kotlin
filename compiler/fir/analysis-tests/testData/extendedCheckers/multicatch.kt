fun foo() {
    try {

    } catch(e: <!CATCH_NOT_THROWABLE!>Int<!>, <!CATCH_NOT_THROWABLE!>Double<!>) {

    }

    try {

    } catch(e: Exception, <!SUBTYPE_IN_CATCH!>NullPointerException<!>) {

    } finally {

    }

    try {

    } catch(e: Throwable, <!SUBTYPE_IN_CATCH!>Exception<!>, <!SUBTYPE_IN_CATCH!>Error<!>) {

    }
}

