package com.mahzooz.monitor;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public class TemplateRepository {
    public static class Entry { String label; long hash; Entry(String l,long h){label=l;hash=h;} }
    public static class Match { public final String label; public final float similarity; Match(String l,float s){label=l;similarity=s;} }
    private final SharedPreferences p;
    private final List<Entry> entries=new ArrayList<>();

    // Built-in visual fingerprints for the known game icons.
    // They are local templates only; relation mapping is learned from observations.
    private static final String[][] BUILTIN = {
        {"عصا مضيئة","0x2700181c1e3e76","Y"},{"شبشب","0x1c0010381c1ece","Y"},{"بطة","0x800001e3e1ecc","Y"},{"عباد الشمس","0x6f00001c1c1ecc","Y"},
        {"إنزال جوي","0x3730000c0e1efe","Y"},{"حقيبة إسعاف","0x7700000e1e3ede","Y"},{"خوذة","0x1c040004263efc","G"},{"مقلاة","0x1c0c00001e1edc","G"},
        {"قطة","0x3f0000001e3e1e5e","Y"},{"قنبلة","0x800000003c3c3cce","Y"},{"يحب","0x1e3e1cdc","Y"},{"بيضة","0x1c3c1cdc","Y"},
        {"طاحونة هوائية","0xc0707870300000","G"},{"حلوى","0xf8f870200000","G"},{"جولدبريك","0x80707070700000","G"},{"مطرقة","0x80e0f0f0710000","G"},
        {"مصاصة","0x3e00083e3e3edc","Y"},{"دونات","0x36001c1c1c1edc","Y"},{"كب كيك","0x1800001e3e3edc","Y"},{"هامبورغ","0x3e00000e1e3efc","Y"},
        {"دراق","0x80000001e005c","G"},{"مانجو","0x800001e1c3850","G"},{"موز","0x1c00003e0e3e58","G"},{"كرز","0x1400103c3e1e5c","G"},
        {"بطيخ","0x40c1c1cc80000","G"},{"عنب","0x1c1e1e1cf80000","G"},{"خوخ","0x4020a3cd00000","G"},{"فراولة","0x183c1e1ecc0000","G"},
        {"حب","0x1c00000d1c3c58","G"},{"الشواء","0xc00001c3e3e5c","G"}
    };

    public TemplateRepository(Context c){p=c.getSharedPreferences("templates",Context.MODE_PRIVATE);load();}
    private void load(){
        entries.clear();
        for(String[] a:BUILTIN) try { entries.add(new Entry(a[0],Long.decode(a[1]))); } catch(Exception ignored) {}
        String raw=p.getString("data","");
        if(!raw.isEmpty()) for(String s:raw.split("\\n")){String[] a=s.split("\\|",2);if(a.length==2)try{entries.add(new Entry(a[1],Long.parseLong(a[0])));}catch(Exception ignored){}}
    }
    private void save(){StringBuilder b=new StringBuilder();for(Entry e:entries)b.append(e.hash).append('|').append(e.label.replace("|","/")).append('\n');p.edit().putString("data",b.toString()).apply();}
    public synchronized void add(String label,long hash){entries.add(new Entry(label,hash));save();}
    public synchronized Match best(long hash){
        String best="غير معروف";float score=0;
        for(Entry e:entries){long x=hash^e.hash;int d=Long.bitCount(x);float s=1f-d/64f;if(s>score){score=s;best=e.label;}}
        return new Match(score>=0.62f?best:"غير معروف",score);
    }
    public synchronized int size(){return entries.size();}
}
