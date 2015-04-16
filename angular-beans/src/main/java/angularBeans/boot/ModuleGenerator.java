/*
 * AngularBeans, CDI-AngularJS bridge 
 *
 * Copyright (c) 2014, Bessem Hmidi. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 */

/**
 @author Bessem Hmidi
 */
package angularBeans.boot;

import java.io.Serializable;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

import angularBeans.api.NGApp;
import angularBeans.api.NGController;
import angularBeans.api.NGModel;
import angularBeans.api.NGModules;
import angularBeans.api.NGPostConstruct;
import angularBeans.api.NGRedirect;
import angularBeans.api.NGReturn;
import angularBeans.api.NGSubmit;
import angularBeans.api.Subscribe;
import angularBeans.context.BeanLocator;
import angularBeans.context.NGSessionScopeContext;
import angularBeans.io.ByteArrayCache;
import angularBeans.io.Call;
import angularBeans.io.FileUpload;
import angularBeans.io.FileUploadHandler;
import angularBeans.io.LobWrapper;
import angularBeans.ngservices.NGExtention;
import angularBeans.realtime.RealTime;
import angularBeans.util.AngularBeansUtil;
import angularBeans.util.CurrentNGSession;
import angularBeans.util.NGControllerBean;
import angularBeans.util.ScopeUtils;
import angularBeans.validation.BeanValidationProcessor;

@SessionScoped
public class ModuleGenerator implements Serializable {

	private String UID;;

	@Inject
	ScopeUtils scopeUtil;

	@Inject
	AngularBeansUtil util;

	public ModuleGenerator() {

		if (this.getClass().equals(ModuleGenerator.class)) {

			UID = String.valueOf(UUID.randomUUID());
		}

		NGSessionScopeContext.setCurrentContext(UID);

	}

	@PostConstruct
	public void init() {

	}

	public synchronized String getUID() {
		return UID;
	}

	@Inject
	ByteArrayCache cache;

	@Inject
	@Any
	@NGApp
	Instance<Object> app;

	@Inject
	BeanLocator locator;

	@Inject
	@NGController
	@Any
	Instance<Object> controllers;

	@Inject
	@Any
	@NGExtention
	Instance<Object> ext;

	@Inject
	FileUploadHandler uploadHandler;

	@Inject
	BeanValidationProcessor validationAdapter;

	@Inject
	ScopeUtils scopeUtils;
	
	@Inject
	CurrentNGSession ngSession;

	private StringWriter writer;

	private HttpServletRequest request;

	private String contextPath;

	public void getScript(StringWriter writer) {

		NGSessionScopeContext.setCurrentContext(UID);

		ngSession.setSessionId(UID);
		
		
		contextPath = (request.getServletContext().getContextPath());

		this.writer = writer;

		String appName = null;

		Class appClass = null;
		for (Object ap : app) {
			ap.toString();

			appClass = ap.getClass();
			if (appClass.isAnnotationPresent(Named.class)) {
				appName = ap.getClass().getAnnotation(Named.class).value();
			}

			if ((appName == null) || (appName.length() < 1)) {

				appName = util.getBeanName(ap.getClass());
			}
		}

		writer.write("var angularBeans={ test:function(){console.log('it work !!');} };");
		writer.write("var app=angular.module('" + appName + "', [");

		if (appClass.isAnnotationPresent(NGModules.class)) {

			NGModules ngModAnno = (NGModules) appClass
					.getAnnotation(NGModules.class);
			String[] modules = ngModAnno.value();
			String modulesPart = "";
			for (String module : modules) {
				modulesPart += ("'" + module + "',");
			}
			modulesPart = modulesPart.substring(0, modulesPart.length() - 1);
			writer.write(modulesPart);
		}

		// ['angularFileUpload']

		writer.write("])");

		writer.write(".run(function($rootScope) {$rootScope.sessionUID = \""
				+ UID + "\";");

		for (String model : scopeUtil.getRootScope().getProperties()) {

			writer.write("$rootScope." + model + " = "
					+ util.getJson(scopeUtil.getRootScope().getProperty(model))
					+ ";");

		}

		writer.write("})");

		for (Object controller : controllers) {

			NGControllerBean mb = new NGControllerBean(controller);

			writer.write(";app.controller('" + mb.getName() + "',");

			generateController(mb, contextPath);

			writer.write("]);\n");
		}

		validationAdapter.build(writer);

		for (Object extention : ext) {

			Method m;
			try {
				extention.toString();
				m = extention.getClass().getMethod("render");
				writer.write(m.invoke(extention) + ";");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

	public void generateController(NGControllerBean ngController,
			String contextPath) {

		Object reference = locator.lookup(ngController.getName(), UID);

		Class<? extends Object> clazz = ngController.getTargetClass();

		Method[] methods = clazz.getDeclaredMethods();
		Object o = reference;

		scopeUtils.addScope(clazz);

		writer.write("['$rootScope','$scope','$http','$location','logger','responseHandler','RTSrvc',function");
		writer.write("($rootScope,$scope, $http, $location,logger,responseHandler");

		writer.write(",RTSrvc){\n");

		writer.write("\nvar rpath='" + contextPath
				+ "/http/invoke/service/';\n");

		String defaultChannel = clazz.getSimpleName();

		writer.write("\nRTSrvc.subscribe($scope,'" + defaultChannel + "');");
		if (clazz.isAnnotationPresent(Subscribe.class)) {
			String[] channels = ((Subscribe) clazz
					.getAnnotation(Subscribe.class)).channels();

			for (String channel : channels) {

				writer.write("RTSrvc.subscribe($scope,'" + channel + "');");
			}
		}

		List<Method> getters = new ArrayList<Method>();

		for (Method m : methods) {
			if (util.isGetter(m)) {
				if(m.isAnnotationPresent(NGModel.class)){
				getters.add(m);
			}
				}
		}

		for (Method get : getters) {
			Object result = null;

			String getter = get.getName();

			String modelName = util.obtainFieldNameFromAccessor(getter);

			if (get.getReturnType().equals(LobWrapper.class)) {

				String uid = String.valueOf(UUID.randomUUID());
				cache.getCache().put(uid, new Call(o, get));
				result = "lob/" + uid;

				writer.write("$scope." + modelName + "='" + result + "';");
				continue;

			}

			validationAdapter.processBeanValidationParsing(get);

			Method m;

			try {

				m = o.getClass().getMethod((getter));

				result = m.invoke(o);

				if ((result == null && (m.getReturnType().equals(String.class))))
					result = "";

				if (result == null)
					continue;

				Class<? extends Object> resultClazz = result.getClass();

				if (!resultClazz.isPrimitive()) {

					result = util.getJson(result);

				}

			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			writer.write("$scope." + modelName + "=" + result + ";");

		}

		for (Method m : methods) {
			if ((!util.isSetter(m)) && (!util.isGetter(m))) {

				String csModel = null;
				String[] csUpdates = null;
				Set<Method> setters = new HashSet<Method>();

				String httpMethod = "get";

				if (m.isAnnotationPresent(FileUpload.class)) {

					String uploadPath = ((FileUpload) m
							.getAnnotation(FileUpload.class)).path();

					Call call = new Call(o, m);

					uploadHandler.getUploadsActions().put(uploadPath, call);
				}

				if (m.isAnnotationPresent(GET.class)) {
					httpMethod = "get";
				}

				if (m.isAnnotationPresent(POST.class)) {
					httpMethod = "post";
				}

				if (m.isAnnotationPresent(DELETE.class)) {
					httpMethod = "delete";
				}

				if (m.isAnnotationPresent(PUT.class)) {
					httpMethod = "put";
				}

				if (m.isAnnotationPresent(NGReturn.class)) {
					NGReturn returns = m.getAnnotation(NGReturn.class);
					csModel = returns.model();
					csUpdates = returns.updates();
				}

				if (m.isAnnotationPresent(NGRedirect.class)) {
					NGRedirect returns = m.getAnnotation(NGRedirect.class);
					csUpdates = returns.updates();
				}

				// if (m.isAnnotationPresent(NGSubmit.class)
				// || m.isAnnotationPresent(NGRedirect.class)) {

				if (m.isAnnotationPresent(NGSubmit.class)) {

					String[] models = m.getAnnotation(NGSubmit.class)
							.backEndModels();

					if (models.length == 1 && models[0].equals("*")) {

						pushScope(methods, setters);

					} else {

						for (String model : models) {

							for (Method md : methods) {

								if (util.isSetter(md)) {
									String methodName = md.getName();
									String modelName = util
											.obtainFieldNameFromAccessor(methodName);
									if (modelName.equals(model)) {
										setters.add(md);
									}

								}

							}
						}
					}
				}
				// else {
				// pushScope(methods, setters);
				// }

				writer.write("\n $scope." + m.getName() + "= function(");
				// ---------------------------------------------

				// Handle args
				// ---------------------------------------------
				Type[] args = m.getParameterTypes();

				if (!m.isAnnotationPresent(FileUpload.class)) {

					if (args.length > 0) {
						String argsString = "";
						for (int i = 0; i < args.length; i++) {

							argsString += ("arg" + i + ",");

						}

						writer.write(argsString.substring(0,
								argsString.length() - 1));

					}
				}
				// ------------------------------------------------
				// -------------------------------

				writer.write(") {");

				writer.write("var params={sessionUID:$rootScope.sessionUID};");
				addParams(setters, m, args);

				if (m.isAnnotationPresent(RealTime.class)) {

					writer.write("RTSrvc.call($scope,'"
							+ ngController.getName() + "." + m.getName()
							+ "',params);");

				} else {

					writer.write("\n  $http." + httpMethod + "(rpath+'"
							+ ngController.getName() + "/" + m.getName()
							+ "/json");

					if (httpMethod.equals("post")) {
						writer.write("',params");
					} else {
						// encodeURI
						String paramsQuery = ("?params='+encodeURIComponent(angular.toJson(params))");

						writer.write(paramsQuery);
					}

					writer.write(").\n success(function(msg) {\n");

					writer.write("var scopes=RTSrvc.getScopes();");

					writer.write("responseHandler.handleResponse(msg,scopes);");

					writer.write("} );"); // }");

				}

				writer.write("};");

				if (m.isAnnotationPresent(NGPostConstruct.class)) {

					writer.write("\n$scope." + m.getName() + "();\n");
				}
			}
		}
		writer.write("\n} \n");

	}

	private void pushScope(Method[] methods, Set<Method> setters) {
		for (Method md : methods) {

			if (util.isSetter(md)) {
				setters.add(md);
			}
		}
	}

	private void addParams(Set<Method> setters, Method m, Type[] args) {

		for (Method setter : setters) {

			String name = util.obtainFieldNameFromAccessor(setter.getName());
			writer.write("params['" + name + "']=$scope." + name + ";");

		}

		// ---------------------------------
		// handle args

		if (args.length > 0) {
			String argsString = "";
			for (int i = 0; i < args.length; i++) {

				argsString += "arg" + i + ",";

			}

			argsString = argsString.substring(0, argsString.length() - 1);

			writer.write("params['args']=[" + argsString + "];\n");

		}
		// }

	}

	public void setHTTPRequest(HttpServletRequest request) {

		request.getSession().setAttribute(util.NG_SESSION_ATTRIBUTE_NAME, UID);

		this.request = request;

	}

	public HttpServletRequest getRequest() {
		return request;
	}
}