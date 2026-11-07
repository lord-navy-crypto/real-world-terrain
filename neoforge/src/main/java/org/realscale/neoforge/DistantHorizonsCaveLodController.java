package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

import net.neoforged.fml.ModList;

/**
 * Optional adapter for Distant Horizons' built-in cave LOD culling.
 *
 * This only changes distant fake-chunk cave visibility. It does not alter
 * vanilla section meshes, worldgen, saved blocks, cave data, or cave height.
 * The feature is opt-in and uses the public DH API reflectively so DH remains
 * an optional dependency.
 */
final class DistantHorizonsCaveLodController {
    private static final boolean ENABLED=Boolean.getBoolean(
            "realscale.experimental.client.underground_lod_culling");
    private static final String DH_MOD_ID="distanthorizons";
    private static final String API_PACKAGE="com.seibel.distanthorizons.api.";

    private static Object caveCullingValue;
    private static Method getApiValue;
    private static Method getCanBeOverrodeByApi;
    private static Method setValueOneArg;
    private static Method setValueTwoArgs;
    private static Method clearValue;
    private static boolean ownsOverride;
    private static Boolean appliedValue;
    private static boolean loggedRefused;
    private static boolean loggedFailure;

    private DistantHorizonsCaveLodController(){}

    static boolean enabled(){return ENABLED;}

    /** Called only from the physical client tick, after conservative classification. */
    static void observe(AdaptiveVisibilityBudget.Mode mode){
        if(!ENABLED)return;
        if(!ModList.get().isLoaded(DH_MOD_ID)){
            restore();
            return;
        }
        if(!resolveApi())return;

        boolean target=AdaptiveVisibilityBudget.shouldCullDistantHorizonCaves(mode);
        try{
            Object currentApiValue=getApiValue.invoke(caveCullingValue);
            if(ownsOverride){
                // If another API user changed/cleared the value, relinquish
                // control instead of fighting it.
                if(!Objects.equals(currentApiValue,appliedValue)){
                    ownsOverride=false;
                    appliedValue=null;
                    caveCullingValue=null;
                    if(!loggedRefused){
                        loggedRefused=true;
                        System.err.println("[RealScale DH cave LOD] another API override changed; relinquishing control");
                    }
                    return;
                }
                if(Objects.equals(appliedValue,target))return;
            }else{
                // Never replace another mod's API override or a non-overridable setting.
                if(currentApiValue!=null
                        ||!Boolean.TRUE.equals(getCanBeOverrodeByApi.invoke(caveCullingValue))){
                    if(!loggedRefused){
                        loggedRefused=true;
                        System.err.println("[RealScale DH cave LOD] config is already API-controlled or not overridable; leaving it unchanged");
                    }
                    return;
                }
            }

            boolean changed=setValue(target);
            if(changed){
                ownsOverride=true;
                appliedValue=target;
                System.err.println("[RealScale DH cave LOD] caveCullingEnabled="+target
                        +" mode="+mode
                        +" (DH distant LOD only; vanilla sections unchanged)");
            }else if(!loggedRefused){
                loggedRefused=true;
                System.err.println("[RealScale DH cave LOD] DH refused the optional config override; leaving its setting unchanged");
            }
        }catch(ReflectiveOperationException|RuntimeException ex){
            failOnce("Could not update DH cave LOD visibility: "+ex.getClass().getSimpleName());
            restore();
        }
    }

    /** Restore the user's DH config when leaving a world or disconnecting. */
    static void restore(){
        if(!ownsOverride||caveCullingValue==null){
            clearLocalState();
            return;
        }
        try{
            Object currentApiValue=getApiValue.invoke(caveCullingValue);
            if(!Objects.equals(currentApiValue,appliedValue)){
                // Another API user changed the setting after us; leave it alone.
                clearLocalState();
                return;
            }
            boolean cleared=false;
            if(clearValue!=null)
                cleared=Boolean.TRUE.equals(clearValue.invoke(caveCullingValue));
            if(!cleared&&setValueOneArg!=null)
                cleared=Boolean.TRUE.equals(setValueOneArg.invoke(
                        caveCullingValue,new Object[]{null}));
            if(!cleared&&setValueTwoArgs!=null)
                cleared=Boolean.TRUE.equals(setValueTwoArgs.invoke(
                        caveCullingValue,null,"RealScale adaptive cave visibility"));
            if(cleared)clearLocalState();
            else failOnce("DH did not release the cave culling override; will retry restoration");
        }catch(ReflectiveOperationException|RuntimeException ex){
            failOnce("Could not restore DH cave culling config: "+ex.getClass().getSimpleName());
        }
    }

    private static void clearLocalState(){
        caveCullingValue=null;
        getApiValue=null;
        getCanBeOverrodeByApi=null;
        setValueOneArg=null;
        setValueTwoArgs=null;
        clearValue=null;
        ownsOverride=false;
        appliedValue=null;
    }

    private static boolean resolveApi(){
        if(caveCullingValue!=null)return true;
        try{
            ClassLoader loader=DistantHorizonsCaveLodController.class.getClassLoader();
            Class<?> delayed=Class.forName(API_PACKAGE+"DhApi$Delayed",false,loader);
            Field configsField=delayed.getField("configs");
            Object configs=configsField.get(null);
            // DH documents these delayed API fields as null until its first init.
            if(configs==null)return false;

            Class<?> configApi=Class.forName(
                    API_PACKAGE+"interfaces.config.IDhApiConfig",false,loader);
            Object graphics=configApi.getMethod("graphics").invoke(configs);
            if(graphics==null)return false;

            Class<?> graphicsApi=Class.forName(
                    API_PACKAGE+"interfaces.config.client.IDhApiGraphicsConfig",false,loader);
            Object value=graphicsApi.getMethod("caveCullingEnabled").invoke(graphics);
            if(value==null)return false;

            Class<?> valueApi=Class.forName(
                    API_PACKAGE+"interfaces.config.IDhApiConfigValue",false,loader);
            getApiValue=valueApi.getMethod("getApiValue");
            getCanBeOverrodeByApi=valueApi.getMethod("getCanBeOverrodeByApi");
            clearValue=find(valueApi,"clearValue",0);
            setValueOneArg=find(valueApi,"setValue",1);
            setValueTwoArgs=find(valueApi,"setValue",2);
            if(setValueOneArg==null&&setValueTwoArgs==null)return false;
            caveCullingValue=value;
            return true;
        }catch(ClassNotFoundException ex){
            failOnce("Distant Horizons API is unavailable; adaptive cave LOD culling is inactive");
            return false;
        }catch(ReflectiveOperationException|RuntimeException ex){
            failOnce("Distant Horizons API could not be resolved: "+ex.getClass().getSimpleName());
            return false;
        }
    }

    private static Method find(Class<?> type,String name,int parameterCount){
        for(Method method:type.getMethods())
            if(method.getName().equals(name)
                    &&method.getParameterCount()==parameterCount)return method;
        return null;
    }

    private static boolean setValue(boolean value)throws ReflectiveOperationException{
        if(setValueOneArg!=null)
            return Boolean.TRUE.equals(setValueOneArg.invoke(caveCullingValue,Boolean.valueOf(value)));
        return Boolean.TRUE.equals(setValueTwoArgs.invoke(
                caveCullingValue,Boolean.valueOf(value),"RealScale adaptive cave visibility"));
    }

    private static void failOnce(String message){
        if(loggedFailure)return;
        loggedFailure=true;
        System.err.println("[RealScale DH cave LOD] "+message);
    }
}
