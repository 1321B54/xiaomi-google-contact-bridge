package local.contactbridge;

import java.util.*;
import static local.contactbridge.Model.*;

/** Three-way diff. Missing contacts never cause a delete or automatic re-creation. */
public final class Planner {
    public static List<Action> plan(List<Contact> left,List<Contact> right,List<Pair> pairs,boolean twoWay) {
        Map<Long,Contact> ls=new LinkedHashMap<>(),rs=new LinkedHashMap<>();
        for(Contact c:left)ls.put(c.id,c);
        for(Contact c:right)rs.put(c.id,c);
        List<Action> out=new ArrayList<>();
        for(Pair p:pairs) {
            Contact l=ls.remove(p.left),r=rs.remove(p.right);
            if(l==null || r==null || changedIdentity(l.sourceId,p.leftSource) || changedIdentity(r.sourceId,p.rightSource)) {
                out.add(new Action(Kind.MISSING,l,r,p)); continue;
            }
            if(!l.hasData()||!r.hasData()){out.add(new Action(Kind.EMPTY,l,r,p));continue;}
            if(p.pending){out.add(new Action(l.hash().equals(r.hash())?Kind.BASELINE:Kind.CONFLICT,l,r,p));continue;}
            boolean lc=!l.hash().equals(p.leftHash),rc=!r.hash().equals(p.rightHash);
            if(!lc&&!rc){if(!Objects.equals(l.sourceId,p.leftSource)||!Objects.equals(r.sourceId,p.rightSource))out.add(new Action(Kind.BASELINE,l,r,p));continue;}
            if(l.hash().equals(r.hash())) out.add(new Action(Kind.BASELINE,l,r,p));
            else if(lc&&!rc)out.add(new Action(Kind.UPDATE_RIGHT,l,r,p));
            else if(rc&&!lc&&twoWay)out.add(new Action(Kind.UPDATE_LEFT,l,r,p));
            else out.add(new Action(Kind.CONFLICT,l,r,p));
        }
        Map<Long,List<Contact>> lCandidates=new HashMap<>(),rCandidates=new HashMap<>();
        Map<String,List<Contact>> identityIndex=new HashMap<>(),hashIndex=new HashMap<>();
        for(Contact r:rs.values())if(r.hasData()) {
            for(String identity:r.identity())identityIndex.computeIfAbsent(identity,k->new ArrayList<>()).add(r);
            hashIndex.computeIfAbsent(r.hash(),k->new ArrayList<>()).add(r);
        }
        for(Contact l:ls.values())if(l.hasData()) {
            Set<Contact> candidates=new LinkedHashSet<>();
            for(String identity:l.identity())candidates.addAll(identityIndex.getOrDefault(identity,List.of()));
            candidates.addAll(hashIndex.getOrDefault(l.hash(),List.of()));
            for(Contact r:candidates) {
                lCandidates.computeIfAbsent(l.id,k->new ArrayList<>()).add(r);
                rCandidates.computeIfAbsent(r.id,k->new ArrayList<>()).add(l);
            }
        }
        Set<Long> claimed=new HashSet<>();
        for(Contact l:ls.values()) {
            List<Contact> candidates=lCandidates.getOrDefault(l.id,List.of());
            if(!l.hasData()){out.add(new Action(Kind.EMPTY,l,null,null));continue;}
            if(candidates.isEmpty()){out.add(new Action(Kind.CREATE_RIGHT,l,null,null));continue;}
            if(candidates.size()!=1 || rCandidates.get(candidates.get(0).id).size()!=1) {
                for(Contact r:candidates)claimed.add(r.id);
                out.add(new Action(Kind.AMBIGUOUS,l,null,null));continue;
            }
            Contact r=candidates.get(0);claimed.add(r.id);
            // Equal names/content alone cannot establish that two records are the same person.
            if(!Model.matches(l,r)){out.add(new Action(Kind.AMBIGUOUS,l,null,null));continue;}
            out.add(new Action(l.hash().equals(r.hash())?Kind.LINK:Kind.CONFLICT,l,r,null));
        }
        if(twoWay)for(Contact r:rs.values())if(!claimed.contains(r.id)&&!rCandidates.containsKey(r.id))
            out.add(new Action(!r.hasData()?Kind.EMPTY:Kind.CREATE_LEFT,null,r,null));
        return out;
    }
    static boolean changedIdentity(String now,String before){ return before!=null&&!before.isEmpty()&&!Objects.equals(now,before); }
}
