import java.util.*;
import local.contactbridge.Model;
import static local.contactbridge.Model.*;

public class CountsTests {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static Contact contact(long id,long aggregate,boolean phone){return new Contact(id,aggregate,"s"+id,phone?List.of(new Row(NAME,new String[]{"Synthetic"}),new Row(PHONE,new String[]{"123"})):List.of(new Row(NAME,new String[]{"Synthetic"})));}
    public static void main(String[] args){
        // Fully synthetic fixture: 8 people with a phone number, 3 name-only people,
        // and 2 extra raw fragments that re-use the aggregates of people 1 and 2.
        List<Contact> account=new ArrayList<>();
        for(int i=1;i<=8;i++)account.add(contact(i,i,true));
        for(int i=9;i<=11;i++)account.add(contact(i,i,false));
        account.add(contact(12,1,true));account.add(contact(13,2,true));
        Counts counts=Model.counts(account);
        check(account.size()==13&&counts.total==11&&counts.withPhone==8&&counts.withoutPhone==3,"13 raw records represent 8 phone people and three name-only people");
        counts=Model.counts(List.of(contact(1,9,false),contact(2,9,true)));
        check(counts.total==1&&counts.withPhone==1&&counts.withoutPhone==0,"a name-only fragment in a phone aggregate is not counted as a separate empty person");
        Contact whitespace=new Contact(1,"s",List.of(new Row(NAME,new String[]{"Synthetic"}),new Row(PHONE,new String[]{"  "}),new Row(EMAIL,new String[]{"test@example.invalid"})));
        check(!whitespace.hasPhone()&&Model.counts(List.of(whitespace)).withoutPhone==1,"blank phone fields do not hide a valid email-only contact from totals");
        Contact old=contact(1,1,true),reaggregated=contact(1,55,true);
        check(old.hash().equals(reaggregated.hash()),"a display aggregation change alone does not change sync content hashes");
        System.out.println("CountsTests passed: 4/4");
    }
}
