/*
 * Copyright 2019 Adobe Systems Incorporated
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

package com.adobe.cq.cloud.testing.it.cf.smoke;

import com.adobe.cq.cloud.testing.it.cf.smoke.rules.CleanUpRule;
import com.adobe.cq.cloud.testing.it.cf.smoke.rules.ContentFragmentRule;
import com.adobe.cq.cloud.testing.it.cf.smoke.rules.InstallPackageRule;
import com.adobe.cq.testing.client.CQClient;
import com.adobe.cq.testing.junit.rules.CQAuthorClassRule;
import com.adobe.cq.testing.junit.rules.CQRule;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.sling.testing.clients.ClientException;
import org.apache.sling.testing.clients.SlingHttpResponse;
import org.apache.sling.testing.clients.util.poller.Polling;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

public class CFUpdateSmokeIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(CFUpdateSmokeIT.class);

    private static final long TIMEOUT = 3000;
    private static final long RETRY_DELAY = 500;

    private static final String PACKAGE_NAME = "com.adobe.cq.cloud.testing.it.cf.smoke";
    private static final String PACKAGE_VERSION = "1.0";
    private static final String PACKAGE_GROUP = "day/cq60/product";

    private static final String TEST_CONTENT_FRAGMENT_PATH = "/content/dam/cfm-sanity-test/en/sample-structured";
    private static final String TEST_CONTENT_FRAGMENT_PARENT_PATH = "/content/dam/cfm-sanity-test/en/";
    private static final String TEST_CONTENT_FRAGMENT_MODEL_PARENT_PATH = "/conf/cfm-sanity-test/settings/dam/cfm/models/";
    private static final String TEST_CONTENT_FRAGMENT_SIMPLE_TEMPLATE = "/conf/cfm-sanity-test/settings/dam/cfm/templates/cfm-sanity-test/jcr:content";
    private static final String TEST_CONTENT_FRAGMENT_COMPLEX_TEMPLATE_PATH = "/conf/cfm-sanity-test/settings/dam/cfm/models/simple-structure";

    private static final String TEST_CONTENT_FRAGMENT_DESCRIPTION = "Test Content Fragment used to test the creation of a Content Fragment.";
    private static final String TEST_VARIATION_DESCRIPTION = "Content Fragment Test Variation.";

    private static final String CONTENT_FRAGMENT_NEW_VALUE = "<p>Some New Value For Content Fragment.</p>";

    // Class rules that install packages
    public static CQAuthorClassRule cqBaseClassRule = new CQAuthorClassRule();
    public static InstallPackageRule installPackageRule = new InstallPackageRule(cqBaseClassRule.authorRule, "/test-content", PACKAGE_NAME, PACKAGE_VERSION, PACKAGE_GROUP);

    @ClassRule
    public static TestRule ruleChain = RuleChain.outerRule(cqBaseClassRule).around(installPackageRule);

    // Rule chain for clean up and content fragment utilities
    public CQRule cqRule = new CQRule();
    public ContentFragmentRule contentFragmentRule = new ContentFragmentRule(cqBaseClassRule.authorRule);
    public CleanUpRule cleanUpRule = new CleanUpRule(cqBaseClassRule.authorRule, TIMEOUT, RETRY_DELAY);

    @Rule
    public TestRule rules = RuleChain.outerRule(cqRule).around(cleanUpRule).around(contentFragmentRule);

    @Test
    public void testUpdateContentFragment() throws ClientException, TimeoutException, InterruptedException {
        LOGGER.info("Testing updating Content Fragment.");

        contentFragmentRule.updateContentFragmentContent(TEST_CONTENT_FRAGMENT_PATH,
                CONTENT_FRAGMENT_NEW_VALUE, // value
                "description", // element
                "", // variation is master
                false, // version - we don't need to test versioning for smoke tests
                "text/html"// content Type
        );
        contentFragmentRule.applyEdit(TEST_CONTENT_FRAGMENT_PATH);

        new Polling(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return updateCheck();
            }
        }).poll(TIMEOUT, RETRY_DELAY);

        LOGGER.info("Updating Content Fragment was successful.");
    }


    private boolean updateCheck() throws ClientException {
        CQClient client = cqBaseClassRule.authorRule.getAdminClient(CQClient.class);

        // verfiy new variation content was updated
        SlingHttpResponse response = client.doGet(TEST_CONTENT_FRAGMENT_PATH + ".infinity.json", 200);

        try {
            JsonFactory jsonFactory = new JsonFactory();
            JsonParser jsonParser = jsonFactory.createParser(response.getContent());

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode json = mapper.readValue(jsonParser, ObjectNode.class);
            if(!json.hasNonNull("jcr:content")) {
                LOGGER.error("Could not find jcr:content node from JSON response of content fragment.");
                return false;
            }

            JsonNode jcrContent = json.get("jcr:content");
            if(!jcrContent.hasNonNull("data")) {
                LOGGER.error("Could not find jcr:content/data node from JSON response of content fragment.");
                return false;
            }

            JsonNode data = jcrContent.get("data");
            if(!data.hasNonNull("master")) {
                LOGGER.error("Could not find jcr:content/data/master node from JSON response of content fragment.");
                return false;
            }

            JsonNode variation = data.get("master");
            if(!variation.hasNonNull("description")) {
                LOGGER.error("Could not find element node from JSON response of content fragment.");
                return false;
            }

            String description = variation.get("description").asText();

            if(!description.equals(CONTENT_FRAGMENT_NEW_VALUE)) {
                return false;
            }

        } catch (IOException e) {
            LOGGER.error("Could not correctly parse response JSON.");
            return false;
        }

        return true;
    }

}
