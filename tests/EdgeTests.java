import java.util.*;
import local.contactbridge.*;
import static local.contactbridge.Model.*;

public class EdgeTests {
    static int passed=0;
    static Contact contact(long id,String name,String phone){return new Contact(id,"s"+id,List.of(new Row(NAME,new String[]{name}),new Row(PHONE,new String[]{phone})));}
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);passed++;}
    public static void main(String[] args) {
        Contact l=contact(1,"A","13800138000"),r=contact(2,"A","+86 138 0013 8000");
        check(matches(l,r),"Chinese mobile prefix matches");
        check(phone("0086 13800138000").equals(phone("13800138000")),"0086 normalization");
        check(phone(null).isEmpty()&&phone("+").isEmpty(),"null and empty numeric input");
        check(phone("13800138000 ext 22").isEmpty()&&phone("abc13800138000").isEmpty(),"extensions and alphabetic dial strings are not identities");
        check(!contact(3,"A","13800138000 ext 22").hash().equals(contact(4,"A","13800138000 ext 23").hash()),"invalid numbers retain distinct payload hashes");
        Pair pair=new Pair(l.id,r.id,l.hash(),r.hash(),l.sourceId,r.sourceId);
        check(Planner.plan(List.of(),List.of(r),List.of(pair),true).stream().allMatch(a->a.kind==Kind.MISSING&&!a.writes()),"missing left is not restored or deleted");
        check(Planner.plan(List.of(l),List.of(),List.of(pair),true).stream().allMatch(a->a.kind==Kind.MISSING&&!a.writes()),"missing right is not restored or deleted");
        check(Planner.plan(List.of(),List.of(r),List.of(),false).isEmpty(),"one-way does not import Google-only contact");
        Contact changed=contact(2,"Changed on Google","13800138000");
        check(Planner.plan(List.of(l),List.of(changed),List.of(pair),false).get(0).kind==Kind.CONFLICT,"one-way must not overwrite source from target change");
        Contact blank=contact(3,"","");
        check(Planner.plan(List.of(blank),List.of(),List.of(),true).get(0).kind==Kind.EMPTY,"blank contact not created");
        Contact reordered=new Contact(9,"other",List.of(l.rows.get(1),l.rows.get(0)));
        check(l.hash().equals(reordered.hash()),"provider row ordering does not trigger updates");
        Contact namesakeLeft=new Contact(30,"l",List.of(new Row(NAME,new String[]{"张伟"}))),namesakeRight=new Contact(31,"r",List.of(new Row(NAME,new String[]{"张伟"})));
        check(!matches(namesakeLeft,namesakeRight),"equal names do not prove identity");
        List<Action> namesakes=Planner.plan(List.of(namesakeLeft),List.of(namesakeRight),List.of(),true);
        check(namesakes.size()==1&&namesakes.get(0).kind==Kind.AMBIGUOUS,"equal name-only records are held without writes or links");
        check(Planner.plan(List.of(namesakeLeft),List.of(),List.of(),true).get(0).kind==Kind.CREATE_RIGHT,"a new name-only record can still be copied");
        Contact repeated=new Contact(32,"r",List.of(l.rows.get(0),l.rows.get(1),l.rows.get(1)));
        check(!l.hash().equals(repeated.hash()),"duplicate row addition is detectable");
        String before=l.hash();l.rows.get(0).data[0]="Changed";
        check(!before.equals(l.hash()),"mutable input is not hidden by stale cached hash");
        List<Contact> ls=new ArrayList<>(),rs=new ArrayList<>();
        for(int i=0;i<2000;i++){ls.add(contact(i,"Left "+i,"1"+String.format("%010d",i)));rs.add(contact(10000+i,"Right "+i,"2"+String.format("%010d",i)));}
        List<Action> large=Planner.plan(ls,rs,List.of(),true);
        check(large.size()==4000&&large.stream().allMatch(Action::writes),"large disjoint accounts remain fully represented");
        System.out.println("EdgeTests passed: "+passed+"/17");
    }
}
