package com.nabajyoti.systemdesign.week2.twoPC;

public class TwoPhaseCommitDemo {
    public static void main(String[] args) {
        commitWhenBothVoteYes();
        abortWhenAliceVotesNo();
        abortWhenBobCrashesDuringPrepare();
        recoverAfterBobMissesCommit();
    }
    private static void commitWhenBothVoteYes() {
        System.out.println("1. both vote yes");
        AccountParticipant alice = new AccountParticipant("Alice", 100);
        AccountParticipant bob = new AccountParticipant("Bob", 20);
        Coordinator coordinator = new Coordinator();
        coordinator.add(alice, -40);
        coordinator.add(bob, 40);
        print(coordinator.execute(), coordinator, alice, bob);
    }
    private static void abortWhenAliceVotesNo() {
        System.out.println("2. Alice votes no");
        AccountParticipant alice = new AccountParticipant("Alice", 30);
        AccountParticipant bob = new AccountParticipant("Bob", 20);
        Coordinator coordinator = new Coordinator();
        coordinator.add(alice, -40);
        coordinator.add(bob, 40);
        print(coordinator.execute(), coordinator, alice, bob);
    }
    private static void abortWhenBobCrashesDuringPrepare() {
        System.out.println("3. Bob crashes during prepare");
        AccountParticipant alice = new AccountParticipant("Alice", 100);
        AccountParticipant bob = new AccountParticipant("Bob", 20);
        bob.crashOnPrepare();
        Coordinator coordinator = new Coordinator();
        coordinator.add(alice, -40);
        coordinator.add(bob, 40);
        print(coordinator.execute(), coordinator, alice, bob);
    }
    private static void recoverAfterBobMissesCommit() {
        System.out.println("4. Bob misses the commit message");
        AccountParticipant alice = new AccountParticipant("Alice", 100);
        AccountParticipant bob = new AccountParticipant("Bob", 20);
        Coordinator coordinator = new Coordinator();
        coordinator.add(alice, -40);
        coordinator.add(bob, 40);
        print(coordinator.executeStoppingBefore("Bob"), coordinator, alice, bob);
        System.out.println("4. after recover");
        print(coordinator.recover(), coordinator, alice, bob);
    }
    private static void print(Decision decision, Coordinator coordinator,
                              AccountParticipant alice, AccountParticipant bob) {
        System.out.println("decision=" + decision + " log=" + coordinator.getLog());
        System.out.println(alice.getName()
                + " balance=" + alice.getBalance()
                + " pending=" + alice.getPendingDelta()
                + " state=" + alice.getState());
        System.out.println(bob.getName()
                + " balance=" + bob.getBalance()
                + " pending=" + bob.getPendingDelta()
                + " state=" + bob.getState());
        System.out.println();
    }
}
