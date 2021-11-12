package io.quarkiverse.cef;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class CefClientActiveCondition {
    final Lock lock;
    final Condition condition;

    int windowActiveCount;

    public CefClientActiveCondition(Lock lock) {
        this.lock = lock;
        this.condition = lock.newCondition();
        windowActiveCount = 0;
    }

    public Condition getCondition() {
        return condition;
    }

    public void onCreate(HTMLFrame frame) {
        windowActiveCount++;
    }

    public boolean onClose(HTMLFrame frame) {
        windowActiveCount--;
        if (windowActiveCount <= 0) {
            lock.lock();
            condition.signalAll();
            lock.unlock();
        }
        return true;
    }
}
