/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.aliyun.oss.integrationtests;

import static com.aliyun.oss.integrationtests.TestConfig.SECOND_ENDPOINT;
import static com.aliyun.oss.integrationtests.TestConstants.NO_SUCH_KEY_ERR;
import static com.aliyun.oss.integrationtests.TestUtils.calcMultipartsETag;
import static com.aliyun.oss.integrationtests.TestUtils.claimUploadId;
import static com.aliyun.oss.integrationtests.TestUtils.composeLocation;
import static com.aliyun.oss.integrationtests.TestUtils.genFixedLengthFile;
import static com.aliyun.oss.integrationtests.TestUtils.genFixedLengthInputStream;
import static com.aliyun.oss.integrationtests.TestUtils.waitForCacheExpiration;
import static com.aliyun.oss.internal.OSSConstants.DEFAULT_OBJECT_CONTENT_TYPE;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.junit.Test;

import com.aliyun.oss.OSSErrorCode;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.AppendObjectRequest;
import com.aliyun.oss.model.AppendObjectResult;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadResult;
import com.aliyun.oss.model.CopyObjectRequest;
import com.aliyun.oss.model.CopyObjectResult;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectAcl;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.ObjectPermission;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;

public class ObjectAclTest extends TestBase {

    private static final CannedAccessControlList[] ACLS = {
        CannedAccessControlList.Default,
        CannedAccessControlList.Private, 
        CannedAccessControlList.PublicRead, 
        CannedAccessControlList.PublicReadWrite 
    };
    
    @Test
    public void testNormalSetObjectAcl() {
        final String key = "normal-set-object-acl";
        final long inputStreamLength = 128 * 1024; //128KB
        
        try {
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            secondClient.putObject(bucketName, key, instream);
            
            for (CannedAccessControlList acl : ACLS) {
                secondClient.setObjectAcl(bucketName, key, acl);
                
                ObjectAcl returnedAcl = secondClient.getObjectAcl(bucketName, key);
                Assert.assertEquals(acl.toString(), returnedAcl.getPermission().toString());
                
                OSSObject object = secondClient.getObject(bucketName, key);
                Assert.assertEquals(inputStreamLength, object.getObjectMetadata().getContentLength());
                object.getObjectContent().close();
            }
            
            // Set to default acl again
            secondClient.setObjectAcl(bucketName, key, CannedAccessControlList.Default);
            ObjectAcl returnedAcl = secondClient.getObjectAcl(bucketName, key);
            Assert.assertEquals(ObjectPermission.Default, returnedAcl.getPermission());
            
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testUnormalSetObjectAcl() {
        try {        
            // Set non-existent object
            final String nonexistentObject = "unormal-set-object-acl";
            try {
                secondClient.setObjectAcl(bucketName, nonexistentObject, CannedAccessControlList.Private);
                Assert.fail("Set object acl should not be successful");
            } catch (OSSException e) {
                Assert.assertEquals(OSSErrorCode.NO_SUCH_KEY, e.getErrorCode());
                Assert.assertTrue(e.getMessage().startsWith(NO_SUCH_KEY_ERR));
            }
            
            // Set unknown permission
            final String unknownPermission = "UnknownPermission";
            try {
                ObjectPermission permission = ObjectPermission.parsePermission(unknownPermission);
                Assert.assertEquals(ObjectPermission.Unknown, permission);
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
            
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testUnormalGetObjectAcl() {
        // Get non-existent object acl
        final String nonexistentObject = "unormal-get-object-acl";
        try {
            secondClient.getObjectAcl(bucketName, nonexistentObject);
            Assert.fail("Get object acl should not be successful");
        } catch (OSSException e) {
            Assert.assertEquals(OSSErrorCode.NO_SUCH_KEY, e.getErrorCode());
            Assert.assertTrue(e.getMessage().startsWith(NO_SUCH_KEY_ERR));
        }
        
        // Get object using default acl
        final String objectUsingDefaultAcl = "object-using-default-acl";
        final long inputStreamLength = 128 * 1024; //128KB
        try {
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            secondClient.putObject(bucketName, objectUsingDefaultAcl, instream);
            ObjectAcl returnedACL = secondClient.getObjectAcl(bucketName, objectUsingDefaultAcl);
            Assert.assertEquals(ObjectPermission.Default, returnedACL.getPermission());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void testPutObjectWithACLHeader() throws IOException {
        final String key = "put-object-with-acl-header";
        final long inputStreamLength = 128 * 1024; //128KB
        
        try {
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setObjectAcl(CannedAccessControlList.PublicRead);
            secondClient.putObject(bucketName, key, instream, metadata);
            OSSObject o = secondClient.getObject(bucketName, key);
            Assert.assertEquals(key, o.getKey());
            
            // Verify uploaded objects acl
            ObjectAcl returnedACL = secondClient.getObjectAcl(bucketName, key);
            Assert.assertEquals(ObjectPermission.PublicRead, returnedACL.getPermission());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void testAppendObjectWithACLHeader() throws IOException {
        final String key = "append-object-with-acl-header";
        final long inputStreamLength = 128 * 1024; //128KB
        
        try {
            // Append at first
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setObjectAcl(CannedAccessControlList.PublicReadWrite);
            AppendObjectRequest appendObjectRequest = new AppendObjectRequest(bucketName, key, instream, metadata);
            appendObjectRequest.setPosition(0L);
            AppendObjectResult appendObjectResult = secondClient.appendObject(appendObjectRequest);
            OSSObject o = secondClient.getObject(bucketName, key);
            Assert.assertEquals(key, o.getKey());
            Assert.assertEquals(inputStreamLength, o.getObjectMetadata().getContentLength());
            Assert.assertEquals(APPENDABLE_OBJECT_TYPE, o.getObjectMetadata().getObjectType());
            if (appendObjectResult.getNextPosition() != null) {
                Assert.assertEquals(inputStreamLength, appendObjectResult.getNextPosition().longValue());
            }
            
            // Append at twice
            final String filePath = genFixedLengthFile(inputStreamLength);
            appendObjectRequest = new AppendObjectRequest(bucketName, key, new File(filePath));
            appendObjectRequest.setPosition(appendObjectResult.getNextPosition());
            appendObjectResult = secondClient.appendObject(appendObjectRequest);
            o = secondClient.getObject(bucketName, key);
            Assert.assertEquals(inputStreamLength * 2, o.getObjectMetadata().getContentLength());
            Assert.assertEquals(APPENDABLE_OBJECT_TYPE, o.getObjectMetadata().getObjectType());
            if (appendObjectResult.getNextPosition() != null) {                
                Assert.assertEquals(inputStreamLength * 2, appendObjectResult.getNextPosition().longValue());
            }
            
            // Verify uploaded objects acl
            ObjectAcl returnedACL = secondClient.getObjectAcl(bucketName, key);
            Assert.assertEquals(ObjectPermission.PublicReadWrite, returnedACL.getPermission());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testCopyObjectWithACLHeader() throws IOException {
        final String sourceBucket = "copy-existing-object-source-bucket";
        final String targetBucket = "copy-existing-object-target-bucket";
        final String sourceKey = "copy-existing-object-source-object";
        final String targetKey = "copy-existing-object-target-object";
        
        final String userMetaKey0 = "user";
        final String userMetaValue0 = "aliy";
        final String userMetaKey1 = "tag";
        final String userMetaValue1 = "copy-object";
        final String contentType = "application/txt";
        
        try {
            secondClient.createBucket(sourceBucket);
            secondClient.createBucket(targetBucket);
            
            byte[] content = { 'A', 'l', 'i', 'y', 'u', 'n' };
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            metadata.setContentType(DEFAULT_OBJECT_CONTENT_TYPE);
            metadata.addUserMetadata(userMetaKey0, userMetaValue0);
            PutObjectResult putObjectResult = secondClient.putObject(sourceBucket, sourceKey, 
                    new ByteArrayInputStream(content), metadata);
            
            ObjectMetadata newObjectMetadata = new ObjectMetadata();
            newObjectMetadata.setContentLength(content.length);
            newObjectMetadata.setContentType(contentType);
            newObjectMetadata.addUserMetadata(userMetaKey1, userMetaValue1);
            newObjectMetadata.setObjectAcl(CannedAccessControlList.PublicRead);
            CopyObjectRequest copyObjectRequest = new CopyObjectRequest(sourceBucket, sourceKey,
                    targetBucket, targetKey);
            copyObjectRequest.setNewObjectMetadata(newObjectMetadata);
            CopyObjectResult copyObjectResult = secondClient.copyObject(copyObjectRequest);
            String sourceETag = putObjectResult.getETag();
            String targetETag = copyObjectResult.getETag();
            Assert.assertEquals(sourceETag, targetETag);
            
            OSSObject ossObject = secondClient.getObject(targetBucket, targetKey);
            newObjectMetadata = ossObject.getObjectMetadata();
            Assert.assertEquals(contentType, newObjectMetadata.getContentType());
            Assert.assertEquals(userMetaValue1, newObjectMetadata.getUserMetadata().get(userMetaKey1));
            
            // Verify uploaded objects acl
            ObjectAcl returnedACL = secondClient.getObjectAcl(targetBucket, targetKey);
            Assert.assertEquals(ObjectPermission.PublicRead, returnedACL.getPermission());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        } finally {
            waitForCacheExpiration(5);
            deleteBucketWithObjects(secondClient, sourceBucket);
            deleteBucketWithObjects(secondClient, targetBucket);
        }
    }
    
    @Test
    public void testUploadMultipartsWithAclHeader() {
        final String key = "normal-upload-multiparts-with-acl-header";
        final int partSize = 128 * 1024;     //128KB
        final int partCount = 10;
        
        try {
            // Initial multipart upload
            String uploadId = claimUploadId(secondClient, bucketName, key);
            
            // Upload parts
            List<PartETag> partETags = new ArrayList<PartETag>();
            for (int i = 0; i < partCount; i++) {
                InputStream instream = genFixedLengthInputStream(partSize);
                UploadPartRequest uploadPartRequest = new UploadPartRequest();
                uploadPartRequest.setBucketName(bucketName);
                uploadPartRequest.setKey(key);
                uploadPartRequest.setInputStream(instream);
                uploadPartRequest.setPartNumber(i + 1);
                uploadPartRequest.setPartSize(partSize);
                uploadPartRequest.setUploadId(uploadId);
                UploadPartResult uploadPartResult = secondClient.uploadPart(uploadPartRequest);                
                partETags.add(uploadPartResult.getPartETag());
            }
            
            // Complete multipart upload
            CompleteMultipartUploadRequest completeMultipartUploadRequest = 
                    new CompleteMultipartUploadRequest(bucketName, key, uploadId, partETags);
            completeMultipartUploadRequest.setObjectACL(CannedAccessControlList.PublicRead);
            CompleteMultipartUploadResult completeMultipartUploadResult =
                    secondClient.completeMultipartUpload(completeMultipartUploadRequest);
            Assert.assertEquals(composeLocation(secondClient, SECOND_ENDPOINT, bucketName, key), 
                    completeMultipartUploadResult.getLocation());
            Assert.assertEquals(bucketName, completeMultipartUploadResult.getBucketName());
            Assert.assertEquals(key, completeMultipartUploadResult.getKey());
            Assert.assertEquals(calcMultipartsETag(partETags), completeMultipartUploadResult.getETag());
            
            // Get uploaded object
            OSSObject o = secondClient.getObject(bucketName, key);
            final long objectSize = partCount * partSize;
            Assert.assertEquals(objectSize, o.getObjectMetadata().getContentLength());
            Assert.assertEquals(calcMultipartsETag(partETags), o.getObjectMetadata().getETag());
            
            // Verify uploaded objects acl
            ObjectAcl returnedACL = secondClient.getObjectAcl(bucketName, key);
            Assert.assertEquals(ObjectPermission.PublicRead, returnedACL.getPermission());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testIllegalObjectAcl() {
        final String dummyKey = "test-illegal-object-acl";
        try {
            secondClient.setObjectAcl(bucketName, dummyKey, null);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof NullPointerException);
        }
    }
    
    @Test
    public void testIgnoredObjectAclHeader() {
        final String dummyKey = "test-ignored-object-acl-header";
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setObjectAcl(null);
            secondClient.putObject(bucketName, dummyKey, new ByteArrayInputStream(new byte[0]), metadata);
            ObjectAcl objectAcl = secondClient.getObjectAcl(bucketName, dummyKey);
            Assert.assertEquals(ObjectPermission.Default, objectAcl.getPermission());
            
            metadata.setObjectAcl(CannedAccessControlList.Private);
            secondClient.putObject(bucketName, dummyKey, new ByteArrayInputStream(new byte[0]), metadata);
            objectAcl = secondClient.getObjectAcl(bucketName, dummyKey);
            Assert.assertEquals(ObjectPermission.Private, objectAcl.getPermission());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }
}
