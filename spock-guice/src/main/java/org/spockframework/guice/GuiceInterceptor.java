/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.spockframework.guice;

import java.lang.reflect.Field;
import java.util.*;

import com.google.inject.*;
import com.google.inject.spi.InjectionPoint;

import spock.guice.UseModules;

import org.spockframework.runtime.intercept.IMethodInterceptor;
import org.spockframework.runtime.intercept.IMethodInvocation;
import org.spockframework.runtime.model.SpeckInfo;
import org.spockframework.util.UnreachableCodeError;

import spock.lang.Shared;

/**
 * Creates a Guice injector, and injects Guice-provided objects into specifications.
 *
 * @author Peter Niederwieser
 */
public class GuiceInterceptor implements IMethodInterceptor {
  private final UseModules useModules;
  private final Set<InjectionPoint> injectionPoints;

  private Injector injector;

  public GuiceInterceptor(SpeckInfo speck, UseModules useModules) {
    this.useModules = useModules;
    injectionPoints = InjectionPoint.forInstanceMethodsAndFields(speck.getReflection());
  }

  public void invoke(IMethodInvocation invocation) throws Throwable {
    switch(invocation.getMethod().getKind()) {
      case SETUP_SPECK:
        createInjector();
        injectValues(invocation.getTarget(), true);
        invocation.proceed();
        break;
      case SETUP:
        injectValues(invocation.getTarget(), false);
        invocation.proceed();
        break;
      default:
        throw new UnreachableCodeError();
    }
  }

  private void createInjector() {
    injector = Guice.createInjector(createModules());
  }
  
  private List<Module> createModules() {
    List<Module> modules = new ArrayList<Module>();
    for (Class<? extends Module> clazz : useModules.value()) {
      try {
        modules.add(clazz.newInstance());
      } catch (InstantiationException e) {
        throw new GuiceExtensionException("Failed to instantiate module '%s'", e).format(clazz.getSimpleName());
      } catch (IllegalAccessException e) {
        throw new GuiceExtensionException("Failed to instantiate module '%s'", e).format(clazz.getSimpleName());
      }
    }
    return modules;
  }

  private void injectValues(Object target, boolean sharedFields) throws IllegalAccessException {
    for (InjectionPoint point : injectionPoints) {
      if (!(point.getMember() instanceof Field))
        throw new GuiceExtensionException("Method injection is not supported; use field injection instead");
      
      Field field = (Field)point.getMember();
      if (field.isAnnotationPresent(Shared.class) != sharedFields) continue;

      Object value = injector.getInstance(point.getDependencies().get(0).getKey());
      field.setAccessible(true);
      field.set(target, value);
    }
  }
}