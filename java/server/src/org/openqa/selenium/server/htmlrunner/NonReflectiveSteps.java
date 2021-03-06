// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.server.htmlrunner;


import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;

import com.thoughtworks.selenium.Selenium;
import com.thoughtworks.selenium.SeleniumException;

import java.util.logging.Logger;

class NonReflectiveSteps {
  private static final Logger LOG = Logger.getLogger("Selenium Core Step");

  private static Supplier<ImmutableMap<String, CoreStepFactory>> STEPS =
    Suppliers.memoize(NonReflectiveSteps::build);

  public ImmutableMap<String, CoreStepFactory> get() {
    return STEPS.get();
  }

  private static ImmutableMap<String, CoreStepFactory> build() {
    ImmutableMap.Builder<String, CoreStepFactory> steps = ImmutableMap.builder();

    CoreStepFactory nextCommandFails = (locator, value) ->
      (selenium, state) -> new NextCommandFails();
    steps.put("assertErrorOnNext", nextCommandFails);
    steps.put("assertFailureOnNext", nextCommandFails);

    CoreStepFactory verifyNextCommandFails = (locator, value) ->
      (selenium, state) -> new VerifyNextCommandFails();
    steps.put("verifyErrorOnNext", verifyNextCommandFails);
    steps.put("verifyFailureOnNext", verifyNextCommandFails);

    steps.put("echo", ((locator, value) -> (selenium, state) -> {
      LOG.info(locator);
      return NextStepDecorator.IDENTITY;
    }));

    steps.put("pause", ((locator, value) -> (selenium, state) -> {
      try {
        long timeout = Long.parseLong(state.expand(locator));
        Thread.sleep(timeout);
        return NextStepDecorator.IDENTITY;
      } catch (NumberFormatException e) {
        return NextStepDecorator.ERROR(
          new SeleniumException("Unable to parse timeout: " + state.expand(locator)));
      } catch (InterruptedException e) {
        System.exit(255);
        throw new CoreRunnerError("We never get this far");
      }
    }));

    steps.put("store", (((locator, value) -> ((selenium, state) -> {
      state.store(state.expand(locator), state.expand(value));
      return null;
    }))));

    return steps.build();
  }

  private static class NextCommandFails extends NextStepDecorator {

    @Override
    public NextStepDecorator evaluate(CoreStep nextStep, Selenium selenium, TestState state) {
      NextStepDecorator actualResult = nextStep.execute(selenium, state);

      // This is kind of fragile. Oh well.
      if (actualResult.equals(NextStepDecorator.IDENTITY)) {
        return NextStepDecorator.ASSERTION_FAILED;
      }
      return NextStepDecorator.IDENTITY;
    }

    @Override
    public boolean isOkayToContinueTest() {
      return true;
    }
  }

  private static class VerifyNextCommandFails extends NextStepDecorator {

    @Override
    public NextStepDecorator evaluate(CoreStep nextStep, Selenium selenium, TestState state) {
      NextStepDecorator actualResult = nextStep.execute(selenium, state);

      // This is kind of fragile. Oh well.
      if (actualResult.equals(NextStepDecorator.IDENTITY)) {
        return NextStepDecorator.VERIFICATION_FAILED;
      }
      return NextStepDecorator.IDENTITY;
    }

    @Override
    public boolean isOkayToContinueTest() {
      return true;
    }
  }
}
