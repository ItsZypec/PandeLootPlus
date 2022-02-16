package net.seyarada.pandeloot.flags;

import net.seyarada.pandeloot.Logger;
import net.seyarada.pandeloot.drops.IDrop;
import net.seyarada.pandeloot.drops.ItemDrop;
import net.seyarada.pandeloot.drops.LootDrop;
import net.seyarada.pandeloot.flags.enums.FlagPriority;
import net.seyarada.pandeloot.flags.enums.FlagTrigger;
import net.seyarada.pandeloot.flags.types.*;
import net.seyarada.pandeloot.utils.EnumUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class FlagPack {

    public static HashMap<String, FlagPack> cache = new HashMap<>();
    public static final HashMap<String, FlagPack> predefinedPacks = new HashMap<>();

    public final HashMap<FlagTrigger, HashMap<IFlag, FlagModifiers>> flags = new HashMap<>();
    public final HashMap<FlagTrigger, HashMap<ICondition,  FlagModifiers>> conditionFlags = new HashMap<>();
    public String stringFlags;

    public void trigger(FlagTrigger trigger, Entity entity, LootDrop lootDrop, IDrop iDrop) {
        if(!flags.containsKey(trigger)) return;
        if(lootDrop!=null) lootDrop.dropEntity = entity;

        // TODO: Improve this so it doesn't needs to loop unnecessarily
        for(FlagPriority priority : FlagPriority.values()) {
            for(Map.Entry<IFlag, FlagModifiers> flagClasses : flags.get(trigger).entrySet()) {
                IFlag flagClass = flagClasses.getKey();
                FlagModifiers flagData = flagClasses.getValue();
                if(flagClass.getClass().getAnnotation(FlagEffect.class).priority()!=priority)
                    continue;

                if(flagClass instanceof IGeneralEvent e) {
                    e.onCallGeneral(flagData, lootDrop, iDrop, trigger);
                }
                if(flagClass instanceof IItemEvent e && iDrop instanceof ItemDrop d && d.item!=null && lootDrop.dropEntity!=null) {
                    e.onCallItem((Item)entity, flagData, lootDrop, d, trigger);
                }
                if(flagClass instanceof IEntityEvent e && entity!=null) {
                    e.onCallEntity(entity, flagData, lootDrop, iDrop, trigger);
                }
                if(flagClass instanceof IPlayerEvent e && lootDrop.p!=null) {
                    e.onCallPlayer(lootDrop.p, flagData, lootDrop, iDrop, trigger);
                }
            }
        }

    }

    public void trigger(FlagTrigger trigger, Entity entity, Player player) {
        if(!flags.containsKey(trigger)) return;

        // TODO: Improve this so it doesn't needs to loop unnecessarily
        for(FlagPriority priority : FlagPriority.values()) {
            for(Map.Entry<IFlag, FlagModifiers> flagClasses : flags.get(trigger).entrySet()) {
                IFlag flagClass = flagClasses.getKey();
                FlagModifiers flagData = flagClasses.getValue();
                if(flagClass.getClass().getAnnotation(FlagEffect.class).priority()!=priority)
                    continue;

                if(flagClass instanceof IGeneralEvent e) {
                    e.onCallGeneral(flagData, null, null, trigger);
                }
                if(flagClass instanceof IItemEvent e) {
                    e.onCallItem((Item)entity, flagData, null, null, trigger);
                }
                if(flagClass instanceof IEntityEvent e && entity!=null) {
                    e.onCallEntity(entity, flagData, null, null, trigger);
                }
                if(flagClass instanceof IPlayerEvent e) {
                    e.onCallPlayer(player, flagData, null, null, trigger);
                }
            }
        }

    }

    // - diamond{onpickup=[message=hello;give=10];explode=true <shape=sphere>;broadcast=hello world!}

    public static FlagPack fromCompact(String line) {
        String lineWithoutItem = line.substring(line.indexOf("{")+1).strip();
        if(lineWithoutItem.contains(" ")) {
            lineWithoutItem = lineWithoutItem.split(" ")[0];
        }
        if(cache.containsKey(lineWithoutItem)) return cache.get(lineWithoutItem);

        FlagPack pack = new FlagPack();
        pack.stringFlags = lineWithoutItem;
        pack.linealReader(lineWithoutItem);
        cache.put(lineWithoutItem, pack);
        return pack;
    }

    public static FlagPack fromExtended(ConfigurationSection config) {
        if(predefinedPacks.containsKey(config.getName())) return predefinedPacks.get(config.getName());

        FlagPack pack = new FlagPack();
        pack.configReader(config);
        predefinedPacks.put(config.getName(), pack);
        return pack;
    }

    void linealReader(String line) {

        StringBuilder builder = new StringBuilder();
        FlagTrigger readingFor = FlagTrigger.onspawn;
        String flagWaitingForData = null;
        HashMap<FlagTrigger, HashMap<String, String>> map = new HashMap<>();
        boolean inModifiers = false;

        long bracketCount = line.chars().filter(ch -> ch == '}').count();
        long visitedBrackets = 0;

        String amount = null;
        String chance = null;
        String damage = null;

        for(int i = 0; i<line.length(); i++) {
            char c = line.charAt(i);

            if(c == '}') visitedBrackets++;

            // End of the flag part
            if(visitedBrackets==bracketCount || i==line.length()-1) {
                writeRawFlagToMap(map, readingFor, flagWaitingForData, builder.toString());
                visitedBrackets++;
                builder = new StringBuilder();
                continue;
            }

            // Post flag part
            if( (line.contains(" ") || line.contains("}")) && visitedBrackets>bracketCount) {
                builder.append(c);
                if(i==line.length()-1 || c == ' ' && builder.toString().trim().length()>0) {
                    builder = new StringBuilder(builder.toString().trim());
                    if(builder.toString().contains(".")) {
                        chance = builder.toString();
                        HashMap<String, String> innerMap = map.get(FlagTrigger.onspawn);
                        innerMap.put("chance", chance);
                        map.put(FlagTrigger.onspawn, innerMap);
                    } else if(amount==null) {
                        amount = builder.toString();
                        HashMap<String, String> innerMap = map.get(FlagTrigger.onspawn);
                        innerMap.put("amount", amount);
                        map.put(FlagTrigger.onspawn, innerMap);
                    } else if(!builder.toString().isEmpty()) {
                        damage = builder.toString();
                        HashMap<String, String> innerMap = map.get(FlagTrigger.onspawn);
                        innerMap.put("damage", damage);
                        map.put(FlagTrigger.onspawn, innerMap);
                    }

                    builder = new StringBuilder();
                }
                continue;
            }

            switch (c) {
                case '<' -> inModifiers = true;
                case '>' -> inModifiers = false;
                case ']' -> {
                    writeRawFlagToMap(map, readingFor, flagWaitingForData, builder.toString());
                    builder = new StringBuilder();
                    flagWaitingForData = null;
                    readingFor = FlagTrigger.onspawn;
                    continue;
                }
                case ';' -> {
                    if(inModifiers) break;

                    if(flagWaitingForData!=null)
                        writeRawFlagToMap(map, readingFor, flagWaitingForData, builder.toString());
                    builder = new StringBuilder();
                    continue;
                }
                case '=' -> {
                    if(inModifiers) break;

                    String id = builder.toString().toLowerCase();

                    if(EnumUtils.isATrigger(id)) {
                        readingFor = FlagTrigger.valueOf(id);
                        flags.put(readingFor, new HashMap<>());
                        i++;
                    } else {
                        flagWaitingForData = id;
                    }

                    builder = new StringBuilder();
                    continue;
                }
            }

            builder.append(c);
        }

        parseRawFlags(map);
    }

    //{onspawn={net.seyarada.pandeloot.flags.effects.VisibilityFlag@5b2bdb43={value=player},
    // net.seyarada.pandeloot.flags.effects.GlowFlag@46ca2202={value=true},
    // net.seyarada.pandeloot.flags.effects.ExplodeFlag@2882d8cb={shape=sphere, value=true},
    // net.seyarada.pandeloot.flags.effects.ColorFlag@640dced1={value=GREEN}}}

    void configReader(ConfigurationSection config) {
        //HashMap<FlagTrigger, HashMap<IFlag, FlagModifiers>> flags = new HashMap<>();
        flags.put(FlagTrigger.onspawn, new HashMap<>());
        for(String s : config.getKeys(false)) {
            if(EnumUtils.isATrigger(s)) {
                FlagTrigger trigger = FlagTrigger.valueOf(s.toLowerCase());
                flags.put(trigger, new HashMap<>());
                ConfigurationSection triggerFlags = config.getConfigurationSection(s);
                for(String f : triggerFlags.getKeys(false)) {
                    IFlag flag = FlagManager.getFromID(f);
                    if(flag==null) continue;
                    FlagModifiers flagValues = processRawDataAndModifiers(triggerFlags.getString(f));
                    if(flag instanceof ICondition condition) {
                        HashMap<ICondition, FlagModifiers> condMap = conditionFlags.getOrDefault(trigger, new HashMap<>());
                        condMap.put(condition, flagValues);
                        conditionFlags.put(trigger, condMap);
                        if( !(flag instanceof IEntityEvent) &&
                                !(flag instanceof IGeneralEvent) &&
                                !(flag instanceof IItemEvent) &&
                                !(flag instanceof IPlayerEvent) &&
                                !(flag instanceof IServerEvent))
                            continue;
                    }
                    flags.get(trigger).put(flag, flagValues);
                }
                continue;
            }
            IFlag flag = FlagManager.getFromID(s);
            if(flag==null) continue;
            FlagModifiers flagValues = processRawDataAndModifiers(config.getString(s));
            if(flag instanceof ICondition condition) {
                HashMap<ICondition, FlagModifiers> condMap = conditionFlags.getOrDefault(FlagTrigger.onspawn, new HashMap<>());
                condMap.put(condition, flagValues);
                conditionFlags.put(FlagTrigger.onspawn, condMap);
                if( !(flag instanceof IEntityEvent) &&
                        !(flag instanceof IGeneralEvent) &&
                        !(flag instanceof IItemEvent) &&
                        !(flag instanceof IPlayerEvent) &&
                        !(flag instanceof IServerEvent))
                    continue;
            }
            flags.get(FlagTrigger.onspawn).put(flag, flagValues);
        }
    }

    void parseRawFlags(HashMap<FlagTrigger, HashMap<String, String>> map) {
        // This takes the raw information from a flag and parses it
        // Input Example: onspawn{give=10,explode=true <shape=sphere>}
        // Output Example: onspawn{give={value=10},explode={value=true,shape=sphere}}

        for(Map.Entry<FlagTrigger, HashMap<String, String>> entry : map.entrySet()) {
            FlagTrigger trigger = entry.getKey();
            HashMap<IFlag, FlagModifiers> triggerMap = new HashMap<>();
            for(Map.Entry<String, String> subEntry : entry.getValue().entrySet()) {
                String flagName = subEntry.getKey();
                if(flagName==null) continue;
                if(flagName.equals("pack")) {
                    String pack = subEntry.getValue();
                    if(!predefinedPacks.containsKey(pack))
                        Logger.log(Level.WARNING, "Predefined pack %s not found", pack);
                    else
                        addPack(predefinedPacks.get(pack));
                    continue;
                }

                IFlag flag = FlagManager.getFromID(flagName);
                if(flag==null) continue;
                FlagModifiers flagValues = processRawDataAndModifiers(subEntry.getValue());
                if(flag instanceof ICondition condition) {
                    HashMap<ICondition, FlagModifiers> condMap = conditionFlags.getOrDefault(trigger, new HashMap<>());
                    condMap.put(condition, flagValues);
                    conditionFlags.put(trigger, condMap);
                    if( !(flag instanceof IEntityEvent) &&
                            !(flag instanceof IGeneralEvent) &&
                            !(flag instanceof IItemEvent) &&
                            !(flag instanceof IPlayerEvent) &&
                            !(flag instanceof IServerEvent))
                        continue;
                }
                triggerMap.put(flag, flagValues);
            }
            if(!triggerMap.isEmpty()) flags.put(trigger, triggerMap);
        }
    }

    FlagModifiers processRawDataAndModifiers(String s) {
        FlagModifiers modifiersMap = new FlagModifiers(this);
        if(!s.contains("<")) {
            modifiersMap.put("value", s);
        } else {
            String flagValue = s.substring(0, s.indexOf("<")).trim();
            modifiersMap.put("value", flagValue);

            String modifiersString = s.substring(s.indexOf("<") + 1, s.indexOf(">"));
            for(String modifier : modifiersString.split(";")) {
                String modKey = modifier.substring(0, modifier.indexOf("="));
                String modValue = modifier.substring(modifier.indexOf("=") + 1);
                modifiersMap.put(modKey, modValue.trim());
            }

        }
        return modifiersMap;
    }



    void writeRawFlagToMap(HashMap<FlagTrigger, HashMap<String, String>> map, FlagTrigger trigger, String flag, String rawData) {
        HashMap<String, String> innerMap = map.getOrDefault(trigger, new HashMap<>());
        innerMap.put(flag, rawData);
        map.put(trigger, innerMap);
    }

    public void addPack(FlagPack pack) {
        flags.putAll(pack.flags);
        conditionFlags.putAll(pack.conditionFlags);
    }

    public boolean passesConditions(FlagTrigger trigger, Entity entity, Player player) {
        if(!conditionFlags.containsKey(trigger)) return true;
        for(Map.Entry<ICondition, FlagModifiers> entry : conditionFlags.get(trigger).entrySet()) {
            ICondition condition = entry.getKey();
            FlagModifiers values = entry.getValue();
            if(!condition.onCheckNoLootDrop(values, entity, player)) return false;
        }

        return true;
    }


    public static class FlagModifiers extends HashMap<String, String> {

        public final FlagPack pack;

        public FlagModifiers(FlagPack pack) {
            this.pack = pack;
        }

        public String getString() {
            return getString("value");
        }

        public String getString(String key) {
            return get(key);
        }

        public int getInt() {
            return getInt("value");
        }

        public int getInt(String key) {
            if(!containsKey(key)) return 0;
            String value = getString(key);
            return Integer.parseInt(value);
        }

        public int getIntOrDefault(String key, int defaultInt) {
            String value = getString(key);
            return (value!=null) ? Integer.parseInt(value) : defaultInt;
        }

        public double getDouble() {
            return getDouble("value");
        }

        public double getDouble(String key) {
            if(!containsKey(key)) return 0;
            String value = getString(key);
            return Double.parseDouble(value);
        }

        public double getDoubleOrDefault(String key, double defaultDouble) {
            String value = getString(key);
            return (value!=null) ? Double.parseDouble(value) : defaultDouble;
        }

        public long getLong() {
            return getLong("value");
        }

        public long getLong(String key) {
            if(!containsKey(key)) return 0;
            String value = getString(key);
            return Long.parseLong(value);
        }

        public long getLongOrDefault(String key, long defaultLong) {
            String value = getString(key);
            return (value!=null) ? Long.parseLong(value) : defaultLong;
        }

        public boolean getBoolean() {
            return getBoolean("value");
        }

        public boolean getBoolean(String key) {
            if(!containsKey(key)) return false;
            String value = getString(key);
            return Boolean.parseBoolean(value);
        }

        public boolean getBooleanOrDefault(String key, boolean defaultBoolean) {
            String value = getString(key);
            return (value!=null) ? Boolean.parseBoolean(value) : defaultBoolean;
        }

    }

    public boolean hasFlag(IFlag flag) {
        if(!flags.containsKey(FlagTrigger.onspawn)) return false;
        return flags.get(FlagTrigger.onspawn).containsKey(flag);
    }

    public FlagModifiers getFlag(IFlag flag) {
        return flags.get(FlagTrigger.onspawn).get(flag);
    }

    public FlagModifiers getFlag(Class<? extends IFlag> classFlag) {
        String id = classFlag.getDeclaredAnnotation(FlagEffect.class).id();
        IFlag flag = FlagManager.getFromID(id);
        return flags.get(FlagTrigger.onspawn).get(flag);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        flags.forEach((trigger, value) -> {
            builder.append(trigger.toString() + "=[");
            value.forEach((flag, flagValue) -> {
                builder.append(FlagManager.getFromClass(flag) + "=");
                builder.append("<");
                flagValue.forEach((mod, modValue) -> {
                    builder.append(mod + "=" + modValue + ";");
                });
                builder.append(">;");
            });
            if(conditionFlags.containsKey(trigger)) {
                conditionFlags.get(trigger).forEach((condition, condValue) -> {
                    builder.append(FlagManager.getFromClass(condition) + "=");
                    builder.append("<");
                    condValue.forEach((mod, modValue) -> {
                        builder.append(mod + "=" + modValue + ";");
                    });
                    builder.append(">;");
                });
            }
            builder.append("];");
        });
        String str = "{" + builder + "}";
        str = str.replace(";>", ">").replace(";]", "]").replace(";}", "}");
        return str;
    }

}