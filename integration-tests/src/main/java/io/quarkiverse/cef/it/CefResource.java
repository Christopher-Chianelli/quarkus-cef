/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.quarkiverse.cef.it;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.quarkiverse.cef.HTMLApp;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@ApplicationScoped
@QuarkusMain
public class CefResource implements QuarkusApplication {

    @Inject
    HTMLApp cefWindow;

    @Override
    public int run(String... args) throws Exception {
        cefWindow.open();
        cefWindow.waitUntilClosed();
        return 0;
    }
}
