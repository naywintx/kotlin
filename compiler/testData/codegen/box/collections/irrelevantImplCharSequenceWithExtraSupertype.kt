// TARGET_BACKEND: JVM
// FILE: AImpl.java
public abstract class AImpl {
    public char charAt(int index) {
        return 'A';
    }

    public int length() { return 56; }
}

// FILE: A.java
public class A extends AImpl implements CharSequence {
    public CharSequence subSequence(int start, int end) {
        return null;
    }
}

// FILE: 1.kt

interface I : CharSequence

class X : A(), I

fun box(): String {
    val x = X()
    if (x.length != 56) return "fail 1"
    if (x[0] != 'A') return "fail 2"
    return "OK"
}
