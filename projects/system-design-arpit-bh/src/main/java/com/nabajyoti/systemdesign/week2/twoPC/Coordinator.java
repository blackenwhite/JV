package com.nabajyoti.systemdesign.week2.twoPC;

import java.util.ArrayList;
import java.util.List;

public class Coordinator {
    private final List<AccountParticipant> participants = new ArrayList<>();
    private final List<Integer> deltas = new ArrayList<>();
    private final List<Decision> log = new ArrayList<>();

    public void add(AccountParticipant participant, int delta) {
        participants.add(participant);
        deltas.add(delta);
    }
    public boolean preparePhase() {
        for (int i = 0; i < participants.size(); i++) {
            AccountParticipant participant = participants.get(i);
            int delta = deltas.get(i);
            try {
                Vote vote = participant.prepare(delta);
                if (vote == Vote.NO) {
                    return false;
                }
            } catch (RuntimeException failure) {
                return false;
            }
        }
        return true;
    }

    public List<Decision> getLog() {
        return log;
    }
    public Decision execute() {
        boolean allVotedYes = preparePhase();
        if (allVotedYes) {
            log.add(Decision.COMMIT);
            for (AccountParticipant participant : participants) {
                participant.commit();
            }
            return Decision.COMMIT;
        }
        log.add(Decision.ABORT);
        for (AccountParticipant participant : participants) {
            participant.abort();
        }
        return Decision.ABORT;
    }

    public Decision recover() {
        if (log.isEmpty()) {
            throw new IllegalStateException("no decision has been written");
        }
        Decision decision = log.get(log.size() - 1);
        if (decision == Decision.COMMIT) {
            for (AccountParticipant participant : participants) {
                participant.commit();
            }
            return Decision.COMMIT;
        }
        for (AccountParticipant participant : participants) {
            participant.abort();
        }
        return Decision.ABORT;
    }
    public Decision executeStoppingBefore(String name) {
        boolean allVotedYes = preparePhase();
        Decision decision = allVotedYes ? Decision.COMMIT : Decision.ABORT;
        log.add(decision);
        for (AccountParticipant participant : participants) {
            if (participant.getName().equals(name)) {
                return decision;
            }
            if (decision == Decision.COMMIT) {
                participant.commit();
            } else {
                participant.abort();
            }
        }
        return decision;
    }

    public static void main(String[] args) {
        AccountParticipant alice = new AccountParticipant("Alice", 100);
        AccountParticipant bob = new AccountParticipant("Bob", 20);
        Coordinator success = new Coordinator();
        success.add(alice, -40);
        success.add(bob, 40);
        Decision firstDecision = success.execute();
        System.out.println("decision=" + firstDecision + " log=" + success.getLog());
        System.out.println(alice.getName()
                + " balance=" + alice.getBalance()
                + " pending=" + alice.getPendingDelta()
                + " state=" + alice.getState());
        System.out.println(bob.getName()
                + " balance=" + bob.getBalance()
                + " pending=" + bob.getPendingDelta()
                + " state=" + bob.getState());
        AccountParticipant poorAlice = new AccountParticipant("Alice", 30);
        AccountParticipant waitingBob = new AccountParticipant("Bob", 20);
        Coordinator failure = new Coordinator();
        failure.add(waitingBob, 40);
        failure.add(poorAlice, -40);
        Decision secondDecision = failure.execute();
        System.out.println("decision=" + secondDecision + " log=" + failure.getLog());
        System.out.println(waitingBob.getName()
                + " balance=" + waitingBob.getBalance()
                + " pending=" + waitingBob.getPendingDelta()
                + " state=" + waitingBob.getState());
        System.out.println(poorAlice.getName()
                + " balance=" + poorAlice.getBalance()
                + " pending=" + poorAlice.getPendingDelta()
                + " state=" + poorAlice.getState());

        AccountParticipant recoveredAlice = new AccountParticipant("Alice", 100);
        AccountParticipant recoveredBob = new AccountParticipant("Bob", 20);
        Coordinator interrupted = new Coordinator();
        interrupted.add(recoveredAlice, -40);
        interrupted.add(recoveredBob, 40);
        Decision interruptedDecision = interrupted.executeStoppingBefore("Bob");
        System.out.println("after crash decision=" + interruptedDecision
                + " log=" + interrupted.getLog());
        System.out.println(recoveredAlice.getName()
                + " balance=" + recoveredAlice.getBalance()
                + " pending=" + recoveredAlice.getPendingDelta()
                + " state=" + recoveredAlice.getState());
        System.out.println(recoveredBob.getName()
                + " balance=" + recoveredBob.getBalance()
                + " pending=" + recoveredBob.getPendingDelta()
                + " state=" + recoveredBob.getState());
        Decision recoveredDecision = interrupted.recover();
        System.out.println("after recover decision=" + recoveredDecision
                + " log=" + interrupted.getLog());
        System.out.println(recoveredAlice.getName()
                + " balance=" + recoveredAlice.getBalance()
                + " pending=" + recoveredAlice.getPendingDelta()
                + " state=" + recoveredAlice.getState());
        System.out.println(recoveredBob.getName()
                + " balance=" + recoveredBob.getBalance()
                + " pending=" + recoveredBob.getPendingDelta()
                + " state=" + recoveredBob.getState());
    }
}
