import local.contactbridge.Model;
import local.contactbridge.Planner;
import local.contactbridge.Model.Action;
import local.contactbridge.Model.Contact;
import local.contactbridge.Model.Kind;
import local.contactbridge.Model.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Plain main-based checks for Planner/Model. No framework, synthetic data only. */
public class CoreTests {
    static int passed = 0;

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError("FAILED: " + what);
    }

    static void ok(String name) {
        passed++;
        System.out.println("ok - " + name);
    }

    static Model.Row name(String v) { return new Model.Row(Model.NAME, new String[]{v}); }
    static Model.Row phone(String v) { return new Model.Row(Model.PHONE, new String[]{v}); }

    static Contact contact(long id, String sourceId, Model.Row... rows) {
        return new Contact(id, sourceId, Arrays.asList(rows));
    }

    static List<Action> plan(List<Contact> left, List<Contact> right, List<Pair> pairs, boolean twoWay) {
        return Planner.plan(left, right, pairs, twoWay);
    }

    static boolean noWrites(List<Action> out) {
        for (Action a : out) if (a.writes()) return false;
        return true;
    }

    public static void main(String[] args) {
        newLeftCopiesRight();
        newRightCopiesLeft();
        equalExistingContactsLink();
        unchangedPairedContactsNoActions();
        leftOnlyChangeUpdatesRight();
        rightOnlyChangeUpdatesLeft();
        differentConcurrentEditsConflict();
        identicalConcurrentEditsBaseline();
        missingPairedContactNoWrites();
        ambiguousSharedPhoneNoWrites();
        System.out.println("CoreTests passed: " + passed + "/10");
    }

    static void newLeftCopiesRight() {
        Contact l = contact(1, "s", name("Alice"), phone("111"));
        List<Action> out = plan(List.of(l), List.of(), List.of(), true);
        check(out.size() == 1, "one action for new left contact");
        check(out.get(0).kind == Kind.CREATE_RIGHT, "new left -> CREATE_RIGHT");
        check(out.get(0).left == l, "CREATE_RIGHT carries left contact");
        ok("new left copies right");
    }

    static void newRightCopiesLeft() {
        Contact r = contact(2, "s", name("Bob"));
        List<Action> out = plan(List.of(), List.of(r), List.of(), true);
        check(out.size() == 1, "one action for new right contact");
        check(out.get(0).kind == Kind.CREATE_LEFT, "new right -> CREATE_LEFT");
        check(out.get(0).right == r, "CREATE_LEFT carries right contact");
        ok("new right copies left");
    }

    static void equalExistingContactsLink() {
        Contact l = contact(1, "s", name("Alice"), phone("111"));
        Contact r = contact(2, "s", name("Alice"), phone("111"));
        List<Action> out = plan(List.of(l), List.of(r), List.of(), true);
        check(out.size() == 1, "one action for equal existing contacts");
        check(out.get(0).kind == Kind.LINK, "equal existing contacts -> LINK");
        ok("equal existing contacts LINK");
    }

    static void unchangedPairedContactsNoActions() {
        Contact l = contact(1, "s", name("Alice"));
        Contact r = contact(2, "s", name("Alice"));
        Pair p = new Pair(1, 2, l.hash(), r.hash(), "s", "s");
        List<Action> out = plan(List.of(l), List.of(r), List.of(p), true);
        check(out.isEmpty(), "unchanged paired contacts produce no actions");
        ok("unchanged paired contacts no actions");
    }

    static void leftOnlyChangeUpdatesRight() {
        Contact lOld = contact(1, "s", name("Alice"));
        Contact l = contact(1, "s", name("Alice"), phone("111"));
        Contact r = contact(2, "s", name("Alice"));
        Pair p = new Pair(1, 2, lOld.hash(), r.hash(), "s", "s");
        List<Action> out = plan(List.of(l), List.of(r), List.of(p), true);
        check(out.size() == 1, "one action for left-only change");
        check(out.get(0).kind == Kind.UPDATE_RIGHT, "left-only change -> UPDATE_RIGHT");
        ok("left-only change UPDATE_RIGHT");
    }

    static void rightOnlyChangeUpdatesLeft() {
        Contact l = contact(1, "s", name("Alice"));
        Contact rOld = contact(2, "s", name("Alice"));
        Contact r = contact(2, "s", name("Alice"), phone("111"));
        Pair p = new Pair(1, 2, l.hash(), rOld.hash(), "s", "s");
        List<Action> out = plan(List.of(l), List.of(r), List.of(p), true);
        check(out.size() == 1, "one action for right-only change");
        check(out.get(0).kind == Kind.UPDATE_LEFT, "right-only change -> UPDATE_LEFT");
        ok("right-only change UPDATE_LEFT");
    }

    static void differentConcurrentEditsConflict() {
        Contact lOld = contact(1, "s", name("Alice"));
        Contact l = contact(1, "s", name("Alice"), phone("111"));
        Contact r = contact(2, "s", name("Alice"), phone("222"));
        Pair p = new Pair(1, 2, lOld.hash(), lOld.hash(), "s", "s");
        List<Action> out = plan(List.of(l), List.of(r), List.of(p), true);
        check(out.size() == 1, "one action for concurrent edits");
        check(out.get(0).kind == Kind.CONFLICT, "different concurrent edits -> CONFLICT");
        ok("different concurrent edits CONFLICT");
    }

    static void identicalConcurrentEditsBaseline() {
        Contact old = contact(1, "s", name("Alice"));
        Contact l = contact(1, "s", name("Alice"), phone("111"));
        Contact r = contact(2, "s", name("Alice"), phone("111"));
        Pair p = new Pair(1, 2, old.hash(), old.hash(), "s", "s");
        List<Action> out = plan(List.of(l), List.of(r), List.of(p), true);
        check(out.size() == 1, "one action for identical concurrent edits");
        check(out.get(0).kind == Kind.BASELINE, "identical concurrent edits -> BASELINE");
        ok("identical concurrent edits BASELINE");
    }

    static void missingPairedContactNoWrites() {
        Contact l = contact(1, "changed", name("Alice"));
        Contact r = contact(2, "s", name("Alice"));
        Pair p = new Pair(1, 2, l.hash(), r.hash(), "original", "s");
        List<Action> out = plan(List.of(l), List.of(r), List.of(p), true);
        check(out.size() == 1, "one action for missing paired contact");
        check(out.get(0).kind == Kind.MISSING, "missing paired contact -> MISSING");
        check(noWrites(out), "MISSING performs no writes");
        ok("missing paired contact MISSING no writes");
    }

    static void ambiguousSharedPhoneNoWrites() {
        Contact l1 = contact(1, "s", name("A"), phone("111"));
        Contact l2 = contact(2, "s", name("B"), phone("111"));
        Contact r = contact(3, "s", name("C"), phone("111"));
        List<Action> out = plan(List.of(l1, l2), List.of(r), List.of(), true);
        check(!out.isEmpty(), "ambiguous match produces actions");
        List<Kind> kinds = new ArrayList<>();
        for (Action a : out) kinds.add(a.kind);
        check(kinds.equals(Arrays.asList(Kind.AMBIGUOUS, Kind.AMBIGUOUS)), "shared phone -> AMBIGUOUS only");
        check(noWrites(out), "AMBIGUOUS performs no writes");
        ok("ambiguous shared phone no writes");
    }
}
