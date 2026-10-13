import java.util.*;
import static local.contactbridge.Model.*;
import local.contactbridge.Planner;

public class JournalTests {
    static Contact c(long id,String name){return new Contact(id,"s"+id,List.of(new Row(NAME,new String[]{name})));}
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args) {
        Contact intended=c(1,"Original"),target=c(2,"Original"),lateSource=c(1,"Late source edit");
        Pair p=afterCopy(intended,lateSource,target,true);
        check(Planner.plan(List.of(lateSource),List.of(target),List.of(p),true).get(0).kind==Kind.UPDATE_RIGHT,"source edit between copy and journal must be propagated");
        Contact lateTarget=c(2,"Late target edit");
        Pair uncertain=afterCopy(intended,intended,lateTarget,true);
        check(uncertain.pending&&Planner.plan(List.of(intended),List.of(lateTarget),List.of(uncertain),true).get(0).kind==Kind.CONFLICT,"unexpected target readback must not silently establish a baseline");
        Contact oldGoogle=c(2,"Original"),newGoogle=c(2,"Late Google edit"),copiedXiaomi=c(1,"Original");
        Pair reverse=afterCopy(oldGoogle,copiedXiaomi,newGoogle,false);
        check(Planner.plan(List.of(copiedXiaomi),List.of(newGoogle),List.of(reverse),true).get(0).kind==Kind.UPDATE_LEFT,"reverse source edit after copy must remain detectable");
        Pair successful=afterCopy(intended,intended,target,true);
        check(!successful.pending&&Planner.plan(List.of(intended),List.of(target),List.of(successful),true).isEmpty(),"completed copy must not loop");
        Pair noSourceId=new Pair(1,2,intended.hash(),target.hash(),null,null);
        check(Planner.plan(List.of(intended),List.of(target),List.of(noSourceId),true).get(0).kind==Kind.BASELINE,"newly assigned cloud source ids should refresh pairing identity");
        System.out.println("JournalTests passed: 5/5");
    }
}
