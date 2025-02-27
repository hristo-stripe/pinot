/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.controller.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.apache.pinot.common.utils.BcryptUtils;
import org.apache.pinot.controller.ControllerTestUtils;
import org.apache.pinot.spi.config.user.ComponentType;
import org.apache.pinot.spi.config.user.RoleType;
import org.apache.pinot.spi.config.user.UserConfig;
import org.apache.pinot.spi.utils.JsonUtils;
import org.apache.pinot.spi.utils.StringUtil;
import org.apache.pinot.spi.utils.builder.UserConfigBuilder;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class PinotAccessControlUserRestletResourceTest {

    private String _createUserUrl;
    private final UserConfigBuilder _userConfigBuilder = new UserConfigBuilder();

    @BeforeClass
    public void setup()
        throws Exception {
        ControllerTestUtils.setupClusterAndValidate();

        _createUserUrl = ControllerTestUtils.getControllerRequestURLBuilder().forUserCreate();
        _userConfigBuilder.setUsername("testUser").setPassword("123456").setComponentType(ComponentType.CONTROLLER)
            .setRoleType(RoleType.USER);
    }

    @Test
    public void testAddUser()
        throws Exception {
        String userconfigString = _userConfigBuilder.setUsername("bad.user.with.dot").build().toJsonString();
        try {
            ControllerTestUtils.sendPostRequest(_createUserUrl, userconfigString);
            Assert.fail("Adding a user with dot in username does not fail");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage().contains(
              "Username: bad.user.with.dot containing '.' or space is not allowed"));
        }

        // Creating an user with a valid username should succeed
        userconfigString = _userConfigBuilder.setUsername("valid_table_name").build().toJsonString();
        ControllerTestUtils.sendPostRequest(_createUserUrl, userconfigString);

        // Create an user that already exists should fail
        try {
            ControllerTestUtils.sendPostRequest(_createUserUrl, userconfigString);
            Assert.fail("Creation of an existing user does not fail");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage().contains("User valid_table_name_CONTROLLER already exists"));
        }
    }

    private UserConfig getUserConfig(String username, String componentType)
        throws Exception {
        String userConfigString =
            ControllerTestUtils.sendGetRequest(ControllerTestUtils.getControllerRequestURLBuilder()
                .forUserGet(username, componentType));
        String usernameWithType = username + "_" + componentType;
        return JsonUtils.jsonNodeToObject(JsonUtils.stringToJsonNode(userConfigString)
            .get(usernameWithType), UserConfig.class);
    }

    @Test
    public void testUpdateUserConfig()
        throws Exception {
        String username = "updateTC";
        String userConfigString = _userConfigBuilder.setUsername(username).setComponentType(ComponentType.CONTROLLER)
            .build().toJsonString();
        ControllerTestUtils.sendPostRequest(_createUserUrl, userConfigString);
        // user creation should succeed
        UserConfig userConfig = getUserConfig(username, "CONTROLLER");
        Assert.assertEquals(userConfig.getRoleType().toString(), RoleType.USER.toString());
        Assert.assertTrue(BcryptUtils.checkpw("123456", userConfig.getPassword()));
        userConfig.setRole("ADMIN");
        userConfig.setPassword("654321");

        JsonNode jsonResponse = JsonUtils.stringToJsonNode(ControllerTestUtils
            .sendPutRequest(ControllerTestUtils.getControllerRequestURLBuilder()
                    .forUpdateUserConfig(username, "CONTROLLER", true),
                userConfig.toString()));
        Assert.assertTrue(jsonResponse.has("status"));

        UserConfig modifiedConfig = getUserConfig(username, "CONTROLLER");
        Assert.assertEquals(modifiedConfig.getRoleType().toString(), "ADMIN");
        Assert.assertTrue(BcryptUtils.checkpw("654321", modifiedConfig.getPassword()));
    }

    @Test
    public void testDeleteUser()
        throws Exception {
        // Case 1: Create a CONTORLLER user and delete it directly w/o using query param.
        UserConfig controllerUserConfig = _userConfigBuilder.setUsername("user1")
            .setComponentType(ComponentType.CONTROLLER).build();
        String creationResponse = ControllerTestUtils
            .sendPostRequest(_createUserUrl, controllerUserConfig.toJsonString());
        Assert.assertEquals(creationResponse, "{\"status\":\"User user1_CONTROLLER has been successfully added!\"}");

        // Delete controller user using CONTROLLER suffix
        String deleteResponse = ControllerTestUtils.sendDeleteRequest(
            StringUtil.join("/", ControllerTestUtils.getControllerBaseApiUrl(),
                "users", "user1?component=CONTROLLER"));
        Assert.assertEquals(deleteResponse, "{\"status\":\"User: user1_CONTROLLER has been successfully deleted\"}");

        // Case 2: Create a BROKER user and delete it directly w/o using query param.
        UserConfig brokerUserConfig = _userConfigBuilder.setUsername("user1").setComponentType(ComponentType.BROKER)
            .build();
        creationResponse = ControllerTestUtils.sendPostRequest(_createUserUrl, brokerUserConfig.toJsonString());
        Assert.assertEquals(creationResponse, "{\"status\":\"User user1_BROKER has been successfully added!\"}");

        // Delete controller user using BROKER suffix
        deleteResponse = ControllerTestUtils.sendDeleteRequest(
            StringUtil.join("/", ControllerTestUtils.getControllerBaseApiUrl(), "users", "user1?component=BROKER")
        );
        Assert.assertEquals(deleteResponse, "{\"status\":\"User: user1_BROKER has been successfully deleted\"}");
    }
}
