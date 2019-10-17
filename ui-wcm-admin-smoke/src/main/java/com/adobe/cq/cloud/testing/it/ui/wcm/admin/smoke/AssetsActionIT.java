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
package com.adobe.cq.cloud.testing.it.ui.wcm.admin.smoke;

import com.adobe.cq.cloud.testing.it.ui.wcm.admin.smoke.rules.CleanUpRule;
import com.adobe.cq.cloud.testing.it.ui.wcm.admin.smoke.rules.TempFolderRule;
import com.adobe.cq.testing.client.CQClient;
import com.adobe.cq.testing.client.JsonClient;
import com.adobe.cq.testing.junit.rules.CQAuthorPublishClassRule;
import com.adobe.cq.testing.junit.rules.CQRule;
import com.adobe.cq.testing.util.TestUtil;
import org.apache.sling.testing.clients.ClientException;
import org.apache.sling.testing.clients.util.ResourceUtil;
import org.apache.sling.testing.clients.util.poller.Polling;
import org.codehaus.jackson.JsonNode;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.TimeoutException;

/**
 * A set of simple asset tests to verify that asset creating is working.
 */
public class AssetsActionIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetsActionIT.class);

    private static final long TIMEOUT = 3000;
    private static final long RETRY_DELAY = 500;

    private static final String ASSET_TO_UPLOAD = "/com/adobe/cq/cloud/testing/it/ui/wcm/admin/smoke/640x480_test_random_image_00aded0f74394cf08773d9d233c16f96.jpg";

    private static CQAuthorPublishClassRule cqAuthorPublishClassRule = new CQAuthorPublishClassRule();

    @ClassRule
    public static TestRule ruleChain = RuleChain.outerRule(cqAuthorPublishClassRule);

    @Rule
    public CQRule cqRule = new CQRule();

    @Rule
    public CleanUpRule cleanUpRule = new CleanUpRule(cqAuthorPublishClassRule.authorRule, TIMEOUT, RETRY_DELAY);

    @Rule
    public TempFolderRule damRootFolderRule = new TempFolderRule(cqAuthorPublishClassRule.authorRule, TIMEOUT, RETRY_DELAY);

    /**
     * <ul> <li>Creates a new Asset below /content/dam/qatestrootfolder.</li> <li>Deletes the Asset as user
     * 'admin'.</li> <li>Verifies that Asset has been correctly deleted</li> </ul>
     *
     * @throws InterruptedException
     * @throws ClientException
     * @throws TimeoutException
     */
    @Test
    public void testCreateDeleteAssetAsAdmin() throws ClientException, InterruptedException {
        CQClient adminAuthor = cqAuthorPublishClassRule.authorRule.getAdminClient(CQClient.class);
        String damRootFolder = damRootFolderRule.getTempFolder();

        LOGGER.info("Testing asset creation through Admin Console.");
        String fileName = ASSET_TO_UPLOAD.substring(ASSET_TO_UPLOAD.lastIndexOf('/') + 1);
        String mimeType = "image/jpeg";

        LOGGER.info("Creating asset..");
        String newFileHandle = adminAuthor.uploadAsset(fileName, ASSET_TO_UPLOAD, mimeType, damRootFolder, 200, 201).getSlingPath();
        cleanUpRule.addPath(newFileHandle);

        try {

            LOGGER.info("Polling for file creation.");

            new Polling(() -> {
                LOGGER.info("Started poll...");
                return checkIfAssetExists(adminAuthor, newFileHandle, ResourceUtil.getResourceAsStream(ASSET_TO_UPLOAD), mimeType);
            }).poll(TIMEOUT, RETRY_DELAY);

            LOGGER.info("File was created properly.");

        } catch(TimeoutException e) {
            LOGGER.error("Polling that file was created reached timeout of {}", TIMEOUT);
        }
    }


    private boolean checkIfAssetExists(CQClient client, String path, InputStream fileData, String mimeType) {
        // Get the root node as JsonNode object
        JsonNode node = null;
        try {
            node = client.adaptTo(JsonClient.class).doGetJson(path, -1);
        } catch (ClientException e) {
            LOGGER.info("Request for " + path + " failed!");
            return false;
        }

        // check if jcr:primaryType is set to dam:Asset
        if(!node.get("jcr:primaryType").getValueAsText().equals("dam:Asset")) {
            LOGGER.info("jcr:primaryType of folder node " + path + " is not set to dam:Asset!");
            return false;
        }

        // check if jcr:content node exists
        if(node.path("jcr:content").isMissingNode()) {
            LOGGER.info("No jcr:content node found below " + path + "!");
            return false;
        }

        // Get the jcr:content node
        node = node.path("jcr:content");
        path += "/jcr:content";

        // check if jcr:primaryType is set to dam:AssetContent
        if(!node.get("jcr:primaryType").getTextValue().equals("dam:AssetContent")) {
            LOGGER.info("jcr:primaryType of jcr:content node below " + path + " is not set to dam:AssetContent!");
            return false;
        }

        // check if metadata node exists
        if(node.path("metadata").isMissingNode()) {
            LOGGER.info("No metadata node found below " + path + "!");
            return false;
        }

        // check if renditions folder exists
        if(node.path("renditions").isMissingNode()) {
            LOGGER.info("No renditions folder found below " + path + "!");
            return false;
        }

        // Get the renditions node
        node = node.path("renditions");
        path += "/renditions";

        // check if original folder exists
        if(node.path("original").isMissingNode()) {
            LOGGER.info("No original folder found below " + path + "!");
            return false;
        }

        // Get the original node
        node = node.path("original");
        path += "/original";

        // check if jcr:primaryType is set to nt:file
        if(!node.get("jcr:primaryType").getTextValue().equals("nt:file")) {
            LOGGER.info("jcr:primaryType of node  " + path + " is not set to nt:file!");
            return false;
        }

        // check if jcr:content node exists
        if(node.path("jcr:content").isMissingNode()) {
            LOGGER.info("No jcr:content node found below " + path + "!");
            return false;
        }

        // Get the jcr:content node
        node = node.path("jcr:content");
        path += "/jcr:content";

        // check if jcr:mimeType is set correctly
        if(!node.get("jcr:mimeType").getTextValue().equals(mimeType)) {
            LOGGER.info("jcr:mimeType is not set to " + mimeType);
            return false;
        }

        try {
            if(!TestUtil.binaryCompare(fileData, client.doStreamGet(path, null, null).getEntity().getContent())) {
                LOGGER.info("The original file and the requested file are not the same");
                return false;
            }
        } catch (Exception e) {
            LOGGER.info("Binary compare of files failed!");
            return false;
        }

        return true;
    }

}
