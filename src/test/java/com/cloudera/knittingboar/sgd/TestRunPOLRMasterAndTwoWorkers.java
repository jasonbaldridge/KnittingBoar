/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.knittingboar.sgd;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.TextInputFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.cloudera.knittingboar.io.InputRecordsSplit;
import com.cloudera.knittingboar.messages.GlobalParameterVectorUpdateMessage;
import com.cloudera.knittingboar.messages.GradientUpdateMessage;
import com.cloudera.knittingboar.utils.TestingUtils;
import com.google.common.io.Files;

/**
 * A simulation of a single POLR master and two POLR workers
 * 
 * @author jpatterson
 *
 */
public class TestRunPOLRMasterAndTwoWorkers {
  private static final Log LOG = LogFactory
      .getLog(TestRunPOLRMasterAndSingleWorker.class.getName());

  private JobConf defaultConf;
  private FileSystem localFs;
  private Configuration configuration;
  private File baseDir;
  private Path workDir;
  private String inputFileName;

  @Before
  public void setup() throws Exception {
    defaultConf = new JobConf();
    defaultConf.set("fs.defaultFS", "file:///");
    localFs = FileSystem.getLocal(defaultConf);
    inputFileName = "kboar-shard-0.txt";
    baseDir = Files.createTempDir();
    File inputFile = new File(baseDir, inputFileName);
    TestingUtils.copyDecompressed(inputFileName + ".gz", inputFile);
    workDir = new Path(baseDir.getAbsolutePath());
    configuration = new Configuration();
    // feature vector size
    configuration.setInt( "com.cloudera.knittingboar.setup.FeatureVectorSize", 10000 );
    configuration.setInt( "com.cloudera.knittingboar.setup.numCategories", 20);
    configuration.setInt("com.cloudera.knittingboar.setup.BatchSize", 200);
    // local input split path
    configuration.set( "com.cloudera.knittingboar.setup.LocalInputSplitPath", "hdfs://127.0.0.1/input/0" );
    configuration.set( "com.cloudera.knittingboar.setup.RecordFactoryClassname", "com.cloudera.knittingboar.records.TwentyNewsgroupsRecordFactory");
/*    // predictor label names
    c.set( "com.cloudera.knittingboar.setup.PredictorLabelNames", "x,y" );

    // predictor var types
    c.set( "com.cloudera.knittingboar.setup.PredictorVariableTypes", "numeric,numeric" );
    
    // target variables
    c.set( "com.cloudera.knittingboar.setup.TargetVariableName", "color" );

    // column header names
    c.set( "com.cloudera.knittingboar.setup.ColumnHeaderNames", "x,y,shape,color,k,k0,xx,xy,yy,a,b,c,bias" );
    //c.set( "com.cloudera.knittingboar.setup.ColumnHeaderNames", "\"x\",\"y\",\"shape\",\"color\",\"k\",\"k0\",\"xx\",\"xy\",\"yy\",\"a\",\"b\",\"c\",\"bias\"\n" );
 */   
  }
  

  @After
  public void teardown() throws Exception {
    FileUtils.deleteQuietly(baseDir);
  }
  
  @Test
  public void testRunMasterAndTwoWorkers() throws Exception {
    // TODO a test with assertions is not a test
    POLRMasterDriver master = new POLRMasterDriver();
    // ------------------    
    // generate the debug conf ---- normally setup by YARN stuff
    master.setConf(configuration);
    // now load the conf stuff into locally used vars
    master.LoadConfigVarsLocally();
    // now construct any needed machine learning data structures based on config
    master.Setup();
    // ------------------    
    
    
    
    POLRWorkerDriver worker_model_builder_0 = new POLRWorkerDriver();
    worker_model_builder_0.internalID = "0";
    // simulates the conf stuff
    worker_model_builder_0.setConf(configuration);
    
    POLRWorkerDriver worker_model_builder_1 = new POLRWorkerDriver();
    worker_model_builder_1.internalID = "1";
    // simulates the conf stuff
    worker_model_builder_1.setConf(configuration);
    
  
    // ---- this all needs to be done in 
    JobConf job = new JobConf(defaultConf);

    long block_size = localFs.getDefaultBlockSize(workDir);
    LOG.info("default block size: " + (block_size / 1024 / 1024) + "MB");
    // ---- set where we'll read the input files from -------------
    FileInputFormat.setInputPaths(job, workDir);
    // try splitting the file in a variety of sizes
    TextInputFormat format = new TextInputFormat();
    format.configure(job);

    InputSplit[] splits = format.getSplits(job, 2);
      
    InputRecordsSplit custom_reader_0 = new InputRecordsSplit(job, splits[0]);
    InputRecordsSplit custom_reader_1 = new InputRecordsSplit(job, splits[1]);
    
      // TODO: set this up to run through the conf pathways
    worker_model_builder_0.setupInputSplit(custom_reader_0);
    worker_model_builder_0.LoadConfigVarsLocally();
    worker_model_builder_0.Setup();
    

    worker_model_builder_1.setupInputSplit(custom_reader_1);
    worker_model_builder_1.LoadConfigVarsLocally();
    worker_model_builder_1.Setup();
    
    
    
    LOG.info( "> Feature Size: " + worker_model_builder_0.FeatureVectorSize );
    LOG.info( "> Category Size: " + worker_model_builder_0.num_categories );
    
    for ( int x = 0; x < 30; x++) {
        
      // run batch 0
        worker_model_builder_0.RunNextTrainingBatch();
        GradientUpdateMessage msg0 = worker_model_builder_0.GenerateUpdateMessage();
        
        worker_model_builder_1.RunNextTrainingBatch();
        GradientUpdateMessage msg1 = worker_model_builder_1.GenerateUpdateMessage();
        
        master.AddIncomingGradientMessageToQueue(msg0);
        master.AddIncomingGradientMessageToQueue(msg1);
        master.RecvGradientMessage(); // process msg
        master.RecvGradientMessage(); // process msg
        
        master.GenerateGlobalUpdateVector();
        
        GlobalParameterVectorUpdateMessage returned_msg = master.GetNextGlobalUpdateMsgFromQueue();
        worker_model_builder_0.ProcessIncomingParameterVectorMessage(returned_msg);
        
        worker_model_builder_1.ProcessIncomingParameterVectorMessage(returned_msg);
        
        LOG.info( "---------- cycle " + x + " done ------------- " );

    } // for
    
  }
  
  
}
