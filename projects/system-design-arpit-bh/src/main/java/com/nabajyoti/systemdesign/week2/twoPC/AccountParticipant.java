package com.nabajyoti.systemdesign.week2.twoPC;

public class AccountParticipant {
    public enum State {
        IDLE,
        PREPARED,
        COMMITTED,
        ABORTED
    }
    private final String name;
    private int balance;
    private Integer pendingDelta;
    private State state;
    private boolean crashOnPrepare;
    public AccountParticipant(String name, int startingBalance) {
        this.name = name;
        this.balance = startingBalance;
        this.pendingDelta = null;
        this.state = State.IDLE;
    }
    public Vote prepare(int delta) {
        if (crashOnPrepare) {
            throw new IllegalStateException(name + " crashed during prepare");
        }
        if (state != State.IDLE) {
            throw new IllegalStateException(name + " cannot prepare while " + state);
        }
        if (balance + delta < 0) {
            return Vote.NO;
        }
        pendingDelta = delta;
        state = State.PREPARED;
        return Vote.YES;
    }
    public String getName() {
        return name;
    }

    public void crashOnPrepare() {
        crashOnPrepare = true;
    }

    public int getBalance() {
        return balance;
    }
    public Integer getPendingDelta() {
        return pendingDelta;
    }
    public State getState() {
        return state;
    }

    public void commit() {
        if (state == State.COMMITTED) {
            return;
        }
        if (state != State.PREPARED) {
            throw new IllegalStateException(name + " cannot commit while " + state);
        }
        balance = balance + pendingDelta;
        pendingDelta = null;
        state = State.COMMITTED;
    }
    public void abort() {
        if (state == State.ABORTED || state == State.IDLE) {
            return;
        }
        if (state != State.PREPARED) {
            throw new IllegalStateException(name + " cannot abort while " + state);
        }
        pendingDelta = null;
        state = State.ABORTED;
    }

    public static void main(String[] args) {
        AccountParticipant alice = new AccountParticipant("Alice", 100);
        alice.prepare(-40);
        alice.commit();
        System.out.println(alice.getName()
                + " balance=" + alice.getBalance()
                + " pending=" + alice.getPendingDelta()
                + " state=" + alice.getState());
        alice.commit();
        System.out.println(alice.getName()
                + " after second commit balance=" + alice.getBalance());
        AccountParticipant carol = new AccountParticipant("Carol", 100);
        carol.prepare(-40);
        carol.abort();
        System.out.println(carol.getName()
                + " balance=" + carol.getBalance()
                + " pending=" + carol.getPendingDelta()
                + " state=" + carol.getState());
        AccountParticipant bob = new AccountParticipant("Bob", 30);
        bob.prepare(-40);
        bob.abort();
        System.out.println(bob.getName()
                + " balance=" + bob.getBalance()
                + " pending=" + bob.getPendingDelta()
                + " state=" + bob.getState());
    }
}
