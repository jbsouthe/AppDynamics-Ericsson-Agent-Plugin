package com.cisco.josouthe;

public class TransactionDetail {
    private Object key;
    private long lastTouchTime;
    private boolean finished=false;

    public TransactionDetail(Object key) {
        setKey(key);
    }

    public Object getKey() {
        return key;
    }

    public void setKey(Object key) {
        this.key = key;
        this.lastTouchTime = System.currentTimeMillis();
    }

    public long getLastTouchTime() {
        return lastTouchTime;
    }

    public void setLastTouchTime(long lastTouchTime) {
        this.lastTouchTime = lastTouchTime;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
        this.lastTouchTime = System.currentTimeMillis();
    }
}
