/*
 * Copyright (C) 2015 HouKx <hkx.aidream@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.pluginmgr;

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;

import com.google.dexmaker.Code;
import com.google.dexmaker.Comparison;
import com.google.dexmaker.DexMaker;
import com.google.dexmaker.FieldId;
import com.google.dexmaker.Label;
import com.google.dexmaker.Local;
import com.google.dexmaker.MethodId;
import com.google.dexmaker.TypeId;
import com.google.dexmaker.dx.dex.DexFormat;

/**
 * 动态生成 插件Activity子类的工具类
 * 
 * @author HouKangxi
 * 
 */
class ActivityClassGenerator {
	private static final String FIELD_ASSERTMANAGER = "mAssertManager";
	private static final String FIELD_RESOURCES = "mResources";
	
	public static void createActivityDex(String superClassName,
			String targetClassName, File saveTo, String pluginId, String pkgName)
			throws IOException {
		byte[] dex = createActivityDex(superClassName, targetClassName,
				pluginId, pkgName);
		if (saveTo.getName().endsWith(".dex")) {
			FileUtil.writeToFile(dex, saveTo);
		} else {
			JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(
					saveTo));
			jarOut.putNextEntry(new JarEntry(DexFormat.DEX_IN_JAR_NAME));
			jarOut.write(dex);
			jarOut.closeEntry();
			jarOut.close();
		}
	}

	/**
	 * 
	 * @param superClassName
	 * @param targetClassName
	 * @param pluginId
	 * @param pkgName
	 * @return
	 */
	public static <S, D extends S> byte[] createActivityDex(
			final String superClassName, final String targetClassName,
			final String pluginId, String pkgName) {

		DexMaker dexMaker = new DexMaker();

		TypeId<D> generatedType = TypeId.get('L' + targetClassName.replace('.',
				'/') + ';');

		TypeId<S> superType = TypeId
				.get('L' + superClassName.replace('.', '/') + ';');
		// 声明类
		dexMaker.declare(generatedType, "", PUBLIC | FINAL, superType);
		// 定义字段
		//private static final String _pluginId = @param{pluginId};
		// private AssetManager asm;
		// private Resources res;
		declareFields(dexMaker, generatedType, superType, pluginId);
		// 声明 默认构造方法
		declare_constructor(dexMaker, generatedType, superType);
	
		// 声明 方法：onCreate
		declareMethod_onCreate(dexMaker, generatedType, superType);
		// 声明 方法：public AssetManager getAssets()
		declareMethod_getAssets(dexMaker, generatedType, superType);
		// 声明 方法：public Resources getResources()
		declareMethod_getResources(dexMaker, generatedType, superType);
		/*
		 * 声明 方法：startActivityForResult(Intent intent, int requestCode, Bundle
		 * options)
		 */
		declareMethod_startActivityForResult(dexMaker, generatedType,superType);
		// 声明 方法：public void onBackPressed()
		declareMethod_onBackPressed(dexMaker, generatedType, superType);
		
		declareMethod_startService(dexMaker, generatedType, superType);
		declareMethod_bindService(dexMaker, generatedType, superType);
		declareMethod_unbindService(dexMaker, generatedType, superType);
		declareMethod_stopService(dexMaker, generatedType, superType);
		// declareMethod_getPackageName(dexMaker, generatedType, superType,
		// pkgName);
		// Create the dex Content
		declareLifeCyleMethod(dexMaker, generatedType, superType, "onResume");
		declareLifeCyleMethod(dexMaker, generatedType, superType, "onStart");
		declareLifeCyleMethod(dexMaker, generatedType, superType, "onRestart");
		declareLifeCyleMethod(dexMaker, generatedType, superType, "onPause");
		declareLifeCyleMethod(dexMaker, generatedType, superType, "onStop");
		declareLifeCyleMethod(dexMaker, generatedType, superType, "onDestroy");

		byte[] dex = dexMaker.generate();
		return dex;
	}

	private static <S, D extends S> void declareFields(
			DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType,
			String pluginId) {
		FieldId<D, String> _pluginId = generatedType.getField(TypeId.STRING,
				"_pluginId");
		dexMaker.declare(_pluginId, PRIVATE | STATIC | FINAL, pluginId);
		
		TypeId<AssetManager> AssetManager = TypeId.get(AssetManager.class);
		TypeId<Resources> Resources = TypeId.get(Resources.class);
		FieldId<D, AssetManager> asm = generatedType.getField(AssetManager,
				FIELD_ASSERTMANAGER);
		dexMaker.declare(asm, PRIVATE, null);
		FieldId<D, Resources> res = generatedType.getField(Resources, FIELD_RESOURCES);
		dexMaker.declare(res, PRIVATE, null);
	}

	// Note: 必须是最后一个Local变量处调用
	private static <D> Local<String> get_pluginId(TypeId<D> generatedType,
			Code methodCode) {
		Local<String> pluginId = methodCode.newLocal(TypeId.STRING);
		FieldId<D, String> fieldId = generatedType.getField(TypeId.STRING,
				"_pluginId");
		methodCode.sget(fieldId, pluginId);
		return pluginId;
	}

	// private static <S,D extends S> void declareMethod_getPackageName(
	// DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType,
	// String pkgName) {
	// MethodId<D, String> methodOveride = generatedType.getMethod(
	// TypeId.STRING, "getPackageName");
	// Code methodCode = dexMaker.declare(methodOveride, PUBLIC);
	// Local<String> local = methodCode.newLocal(TypeId.STRING);
	// methodCode.loadConstant(local, pkgName);
	// methodCode.returnValue(local);
	// }
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static <S, D extends S> void declareMethod_onCreate(
			DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType) {
		TypeId<AssetManager> AssetManager = TypeId.get(AssetManager.class);
		TypeId<Resources> Resources = TypeId.get(Resources.class);
//		TypeId<Theme> Theme = TypeId.get(Theme.class);
		FieldId<D, AssetManager> assertManager = generatedType.getField(AssetManager,
				FIELD_ASSERTMANAGER);
		FieldId<D, Resources> resources = generatedType.getField(Resources, FIELD_RESOURCES);
		//
		// 声明 方法：onCreate
		TypeId<Bundle> Bundle = TypeId.get(Bundle.class);
		TypeId<ActivityOverider> ActivityOverider = TypeId
				.get(ActivityOverider.class);
		TypeId<DisplayMetrics> DisplayMetrics = TypeId
				.get(DisplayMetrics.class);
		TypeId<Configuration> Configuration = TypeId.get(Configuration.class);

		MethodId<D, Void> method = generatedType.getMethod(TypeId.VOID,
				"onCreate", Bundle);
		Code methodCode = dexMaker.declare(method, PROTECTED);
		// locals -- 一个方法内的本地变量必须提前声明在所有操作之前
		Local<D> localThis = methodCode.getThis(generatedType);
		Local<Bundle> lcoalBundle = methodCode.getParameter(0, Bundle);
		Local<AssetManager> localAsm = methodCode.newLocal(AssetManager);
		Local<Resources> superRes = methodCode.newLocal(Resources);
		Local<DisplayMetrics> mtrc = methodCode.newLocal(DisplayMetrics);
		Local<Configuration> cfg = methodCode.newLocal(Configuration);
		Local<Resources> resLocal = methodCode.newLocal(Resources);
//		Local<Theme> localTheme = methodCode.newLocal(Theme);
//		Local<Theme> superTheme = methodCode.newLocal(Theme);
		Local<String> pluginId = get_pluginId(generatedType, methodCode);
		// ActivityOverider:
		// public static AssetManager getAssetManager(String,Activity)
		MethodId<ActivityOverider, AssetManager> methodOveride = ActivityOverider
				.getMethod(AssetManager, "getAssetManager", TypeId.STRING,
						TypeId.get(Activity.class));
		//
		methodCode.invokeStatic(methodOveride, localAsm, pluginId, localThis);
		methodCode.iput(assertManager, localThis, localAsm);
		MethodId methodGetResources = superType.getMethod(Resources,
				"getResources");
		methodCode.invokeSuper(methodGetResources, superRes, localThis);

		//
		// superRes.getDisplayMetrics()
		MethodId<Resources, DisplayMetrics> getDisplayMetrics = Resources
				.getMethod(DisplayMetrics, "getDisplayMetrics");
		methodCode.invokeVirtual(getDisplayMetrics, mtrc, superRes);
		//
		// superRes.getConfiguration()
		MethodId<Resources, Configuration> getConfiguration = Resources
				.getMethod(Configuration, "getConfiguration");
		methodCode.invokeVirtual(getConfiguration, cfg, superRes);
		//
		// res = new Resources(asm, superRes.getDisplayMetrics(),
		// superRes.getConfiguration());

		MethodId<Resources, Void> res_constructor = Resources.getConstructor(
				AssetManager, DisplayMetrics, Configuration);
		methodCode.newInstance(resLocal, res_constructor, localAsm, mtrc, cfg);
		methodCode.iput(resources, localThis, resLocal);
		
		MethodId<ActivityOverider, Void> method_call_onCreate = ActivityOverider
				.getMethod(TypeId.VOID, "callback_onCreate", TypeId.STRING,
						TypeId.get(Activity.class));
		methodCode
				.invokeStatic(method_call_onCreate, null, pluginId, localThis);
		
		// super.onCreate()
		MethodId superMethod = superType.getMethod(TypeId.VOID, "onCreate",
				Bundle);
		methodCode.invokeSuper(superMethod, null, localThis, lcoalBundle);
	
		methodCode.returnVoid();
	}

	private static <S, D extends S> void declareMethod_getResources(
			DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType) {
		TypeId<Resources> Resources = TypeId.get(Resources.class);
		MethodId<D, Resources> getResources = generatedType.getMethod(
				Resources, "getResources");
		Code code = dexMaker.declare(getResources, PUBLIC);
		Local<D> localThis = code.getThis(generatedType);
		Local<Resources> localRes = code.newLocal(Resources);
		Local<Resources> nullV = code.newLocal(Resources);
		code.loadConstant(nullV, null);
		FieldId<D, Resources> res = generatedType.getField(Resources, FIELD_RESOURCES);
		code.iget(res, localRes, localThis);
		//
		Label localResIsNull = new Label();
		code.compare(Comparison.NE, localResIsNull, localRes, nullV);
		MethodId<S, Resources> superGetResources = superType.getMethod(
				Resources, "getResources");
		code.invokeSuper(superGetResources, localRes, localThis);
		code.mark(localResIsNull);
		//
		code.returnValue(localRes);
	}

	private static <S, D extends S> void declareMethod_getAssets(
			DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType) {
		TypeId<AssetManager> AssetManager = TypeId.get(AssetManager.class);
		MethodId<D, AssetManager> getAssets = generatedType.getMethod(
				AssetManager, "getAssets");
		Code code = dexMaker.declare(getAssets, PUBLIC);
		Local<D> localThis = code.getThis(generatedType);
		Local<AssetManager> localAsm = code.newLocal(AssetManager);
		Local<AssetManager> nullV = code.newLocal(AssetManager);
		code.loadConstant(nullV, null);
		FieldId<D, AssetManager> res = generatedType.getField(AssetManager,
				FIELD_ASSERTMANAGER);
		code.iget(res, localAsm, localThis);
		Label localAsmIsNull = new Label();
		code.compare(Comparison.NE, localAsmIsNull, localAsm, nullV);
		MethodId<S, AssetManager> superGetAssetManager = superType.getMethod(
				AssetManager, "getAssets");
		code.invokeSuper(superGetAssetManager, localAsm, localThis);
		code.mark(localAsmIsNull);
		code.returnValue(localAsm);
	}

	private static <S, D extends S> void declare_constructor(DexMaker dexMaker,
			TypeId<D> generatedType, TypeId<S> superType) {
		MethodId<D, Void> method = generatedType.getConstructor();
		Code constructorCode = dexMaker.declare(method, PUBLIC);
		Local<D> localThis = constructorCode.getThis(generatedType);
		MethodId<S, Void> superConstructor = superType.getConstructor();
		constructorCode.invokeDirect(superConstructor, null, localThis);
		constructorCode.returnVoid();// void 方法也必须显式的声明返回void
	}

	private static <S, D extends S> void declareMethod_startActivityForResult(
			DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType) {
		TypeId<Intent> intent = TypeId.get(Intent.class);
		TypeId<Integer> requestCode = TypeId.INT;
		TypeId<Bundle> bundle = TypeId.get(Bundle.class);
		
		TypeId<?>[] params;
		String methodName = "startActivityForResult";
		final boolean isNewSdk = android.os.Build.VERSION.SDK_INT > 10;
		if (isNewSdk) {
			params = new TypeId[] { intent, requestCode, bundle };
		} else {
			params = new TypeId[] { intent, requestCode };
		}
		MethodId<D, Void> method = generatedType.getMethod(TypeId.VOID,
				methodName, params);
		MethodId<S, Void> superMethod = superType.getMethod(TypeId.VOID,
				methodName, params);
		Code methodCode = dexMaker.declare(method, PUBLIC);
		TypeId<ActivityOverider> ActivityOverider = TypeId
				.get(ActivityOverider.class);
		MethodId<ActivityOverider, Intent> methodOveride = ActivityOverider
				.getMethod(intent, "overrideStartActivityForResult",
						TypeId.get(Activity.class),TypeId.STRING,
						intent, requestCode, bundle);
		// locals
		Local<D> localThis = methodCode.getThis(generatedType);
		Local<Intent> newIntent = methodCode.newLocal(intent);
		Local<Bundle> nullParamBundle = methodCode.newLocal(bundle);
		Local<String> pluginId = get_pluginId(generatedType, methodCode);
		methodCode.loadConstant(nullParamBundle, null);
		Local<?> args[];
		if (isNewSdk) {
			args = new Local[] {localThis
					, pluginId
					, methodCode.getParameter(0, intent)//
					, methodCode.getParameter(1, requestCode)//
					, methodCode.getParameter(2, bundle)//
					};
			methodCode.invokeStatic(methodOveride, newIntent, args);
			// super.startActivityForResult(...)
			
			methodCode.invokeSuper(superMethod, null,
					localThis//
					, newIntent//
					, methodCode.getParameter(1, requestCode)//
					, methodCode.getParameter(2, bundle) //
					);
		} else {
			args = new Local[] {localThis
					, pluginId
					, methodCode.getParameter(0, intent)//
					, methodCode.getParameter(1, requestCode)//
					,nullParamBundle
					};
			methodCode.invokeStatic(methodOveride, newIntent, args);
			methodCode.invokeSuper(superMethod, null,
					localThis//
					, newIntent//
					, methodCode.getParameter(1, requestCode)//
					);
		}
		methodCode.returnVoid();
	}

	/**
	 * 生成以下代码： <br/>
	 * 
	 * <pre>
	 * public void onBackPressed() {
	 * 	if (ActivityOverider.overrideOnbackPressed(this, pluginId)) {
	 * 		super.onBackPressed();
	 * 	}
	 * }
	 * </pre>
	 * 
	 * @param dexMaker
	 * @param generatedType
	 * @param superType
	 */

	private static <S, D extends S> void declareMethod_onBackPressed(
			DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType) {
		TypeId<ActivityOverider> ActivityOverider = TypeId
				.get(ActivityOverider.class);
		MethodId<D, Void> method = generatedType.getMethod(TypeId.VOID,
				"onBackPressed");
		Code methodCode = dexMaker.declare(method, PUBLIC);
		// locals -- 一个方法内的本地变量必须提前声明在所有操作之前
		Local<D> localThis = methodCode.getThis(generatedType);
		Local<Boolean> localBool = methodCode.newLocal(TypeId.BOOLEAN);
		Local<Boolean> localFalse = methodCode.newLocal(TypeId.BOOLEAN);
		Local<String> pluginId = get_pluginId(generatedType, methodCode);

		methodCode.loadConstant(localFalse, false);

		MethodId<ActivityOverider, Boolean> methodOveride = ActivityOverider
				.getMethod(TypeId.BOOLEAN, "overrideOnbackPressed"
						, TypeId.get(Activity.class), TypeId.STRING);
		methodCode.invokeStatic(methodOveride, localBool, localThis, pluginId);
		// codeBlock: if start
		Label localBool_isInvokeSuper = new Label();
		methodCode.compare(Comparison.EQ, localBool_isInvokeSuper, localBool,
				localFalse);
		MethodId<S, Void> superMethod = superType.getMethod(TypeId.VOID,
				"onBackPressed");
		methodCode.invokeSuper(superMethod, null, localThis);
		methodCode.mark(localBool_isInvokeSuper);
		// codeBlock: if end
		methodCode.returnVoid();
	}
	
	private static <S, D extends S> void declareMethod_startService(
			DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType) {
		TypeId<ActivityOverider> ActivityOverider = TypeId
				.get(ActivityOverider.class);
		TypeId<ComponentName> returnType = TypeId.get(ComponentName.class);
		TypeId<Intent> Intent = TypeId.get(Intent.class);
		MethodId<D, ComponentName> method = generatedType.getMethod(returnType,
				"startService",Intent);
		MethodId<ActivityOverider, ComponentName> methodOveride = ActivityOverider
				.getMethod(returnType, "overrideStartService"
						,TypeId.get(Activity.class),TypeId.STRING
						,Intent);
		Code methodCode = dexMaker.declare(method, PUBLIC);
		// locals
		Local<D> localThis = methodCode.getThis(generatedType);
		Local<ComponentName> localComponentName = methodCode.newLocal(returnType);
		Local<String> pluginId = get_pluginId(generatedType, methodCode);
		
		methodCode.invokeStatic(methodOveride,
				localComponentName//
				,localThis, pluginId
				, methodCode.getParameter(0, Intent)
				);
		methodCode.returnValue(localComponentName);
	}
	
	private static <S, D extends S> void declareMethod_bindService(
			DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType) {
		//boolean bindService(intent, conn, flags);
		TypeId<ActivityOverider> ActivityOverider = TypeId
				.get(ActivityOverider.class);
		TypeId<Boolean> returnType = TypeId.BOOLEAN;
		TypeId<Intent> Intent = TypeId.get(Intent.class);
		TypeId<ServiceConnection> Conn = TypeId.get(ServiceConnection.class);
		MethodId<D, Boolean> method = generatedType.getMethod(returnType,
				"bindService",Intent,Conn,TypeId.INT);
		MethodId<ActivityOverider, Boolean> methodOveride = ActivityOverider
				.getMethod(returnType, "overrideBindService"
						,TypeId.get(Activity.class),TypeId.STRING
						,Intent,Conn,TypeId.INT);
		Code methodCode = dexMaker.declare(method, PUBLIC);
		// locals
		Local<D> localThis = methodCode.getThis(generatedType);
		Local<Boolean> localBool = methodCode.newLocal(returnType);
		Local<String> pluginId = get_pluginId(generatedType, methodCode);
		
		methodCode.invokeStatic(methodOveride,
				localBool//
				,localThis, pluginId
				, methodCode.getParameter(0, Intent)
				, methodCode.getParameter(1, Conn)
				, methodCode.getParameter(2, TypeId.INT)
				);
		methodCode.returnValue(localBool);
	}
	
	private static <S, D extends S> void declareMethod_unbindService(
			DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType) {
		//void unbindService( conn);
		TypeId<ActivityOverider> ActivityOverider = TypeId
				.get(ActivityOverider.class);
		TypeId<ServiceConnection> Conn = TypeId.get(ServiceConnection.class);
		MethodId<D, Void> method = generatedType.getMethod(TypeId.VOID,
				"unbindService",Conn);
		MethodId<ActivityOverider, Void> methodOveride = ActivityOverider
				.getMethod(TypeId.VOID, "overrideUnbindService"
						,TypeId.get(Activity.class),TypeId.STRING
						,Conn);
		Code methodCode = dexMaker.declare(method, PUBLIC);
		// locals
		Local<D> localThis = methodCode.getThis(generatedType);
		Local<String> pluginId = get_pluginId(generatedType, methodCode);
		
		methodCode.invokeStatic(methodOveride,
				null//
				,localThis, pluginId
				, methodCode.getParameter(0, Conn)
				);
		methodCode.returnVoid();
	}
	
	private static <S, D extends S> void declareMethod_stopService(
			DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType) {
		//boolean stopService(intent);
		TypeId<ActivityOverider> ActivityOverider = TypeId
				.get(ActivityOverider.class);
		TypeId<Boolean> returnType = TypeId.BOOLEAN;
		TypeId<Intent> Intent = TypeId.get(Intent.class);
		//
		MethodId<D, Boolean> method = generatedType.getMethod(returnType,
				"stopService",Intent);
		MethodId<ActivityOverider, Boolean> methodOveride = ActivityOverider
				.getMethod(returnType, "overrideStopService"
						,TypeId.get(Activity.class),TypeId.STRING
						,Intent);
		Code methodCode = dexMaker.declare(method, PUBLIC);
		// locals
		Local<D> localThis = methodCode.getThis(generatedType);
		Local<Boolean> localBool = methodCode.newLocal(returnType);
		Local<String> pluginId = get_pluginId(generatedType, methodCode);
		
		methodCode.invokeStatic(methodOveride,
				localBool//
				,localThis, pluginId
				, methodCode.getParameter(0, Intent)
				);
		methodCode.returnValue(localBool);
	}
	
	private static <S, D extends S> void declareLifeCyleMethod(
			DexMaker dexMaker, TypeId<D> generatedType, TypeId<S> superType,
			String methodName) {
		TypeId<ActivityOverider> ActivityOverider = TypeId
				.get(ActivityOverider.class);
		MethodId<D, Void> method = generatedType.getMethod(TypeId.VOID,
				methodName);
		Code methodCode = dexMaker.declare(method, PROTECTED);
		// locals
		Local<D> localThis = methodCode.getThis(generatedType);
		Local<String> pluginId = get_pluginId(generatedType, methodCode);
		MethodId<S, Void> superMethod = superType.getMethod(TypeId.VOID,
				methodName);
		methodCode.invokeSuper(superMethod, null, localThis);

		MethodId<ActivityOverider, Void> methodOveride = ActivityOverider
				.getMethod(TypeId.VOID, "callback_" + methodName,
						TypeId.STRING, TypeId.get(Activity.class));
		methodCode.invokeStatic(methodOveride, null, pluginId, localThis);
		methodCode.returnVoid();
	}

}
