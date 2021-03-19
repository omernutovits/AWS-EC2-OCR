package ocrdemo;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;


import java.io.*;
import java.util.*;

public class local_application {


    public static String GetManager(AmazonEC2 ec2){
        String manager = null;


//      continue with key after we got the manager


        List<String> valuesT1 = new ArrayList<>();
        valuesT1.add("manager");
        List<String> valuesT2 = new ArrayList<>();
        valuesT2.add("running");
        valuesT2.add("pending");
        Filter filter_manager = new Filter("tag:manager", valuesT1);
        Filter filter_running = new Filter("instance-state-name",valuesT2);

        DescribeInstancesRequest request = new DescribeInstancesRequest().withFilters(filter_manager,filter_running);


        DescribeInstancesResult result = ec2.describeInstances(request.withFilters(filter_manager,filter_running));

        List<Reservation> reservations = result.getReservations();

        for (Reservation reservation : reservations) {
            List<Instance> instances = reservation.getInstances();

            for (Instance instance : instances) {

                manager = instance.getInstanceId();
                System.out.println("manager: "+ manager);


            }
        }
        return manager;
    }


    public static AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
    public static AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
    public static AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();

    public static void main(String[] args) throws IOException {

        String managerID = GetManager(ec2);
        String local_to_managerSQS = null;



        //check if a manager instance is active
        if(managerID == null) {
            Tag tag = new Tag();
            tag.setKey("manager");
            tag.setValue("manager");

            Tag tag_name = new Tag();
            tag_name.setKey("Name");
            tag_name.setValue("manager");
            CreateTagsRequest tagsRequest = new CreateTagsRequest().withTags(tag,tag_name);

            tagsRequest.withTags(tag,tag_name);

            TagSpecification tag_specification = new TagSpecification();

            IamInstanceProfileSpecification spec = new IamInstanceProfileSpecification()
                    .withName("worker_and_sons");

            String userData = "";
            userData = userData + "#!/bin/bash" + "\n";
            userData = userData + "wget https://omertzukijarbucket.s3.amazonaws.com/ManagerApp.jar" + "\n";
            userData = userData + "java -jar ManagerApp.jar" + "\n";
            String base64UserData = null;
            try {
                base64UserData = new String(Base64.getEncoder().encode(userData.getBytes( "UTF-8" )), "UTF-8" );
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }




            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
            runInstancesRequest.withImageId("ami-050a9f83b573a6eb2")
                    .withInstanceType(InstanceType.T2Micro)
                    .withMinCount(1).withMaxCount(1)
                    .withKeyName("omer_and_tzuki")  //TODO ?????
                    .withSecurityGroupIds("sg-4d22bd78")
                    .withTagSpecifications(tag_specification)
                    .withIamInstanceProfile(spec)
                    .withUserData(base64UserData);




            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            managerID = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();


            CreateTagsRequest tag_request = new CreateTagsRequest()
                    .withTags(tag,tag_name)
                    .withResources(managerID);

            ec2.createTags(tag_request);

            local_to_managerSQS = sqs.createQueue("local-to-manager-sqs" + managerID).getQueueUrl();

            System.out.println("===================================== MANAGER CREATED =================================================");
        }
        else{
            try{
            local_to_managerSQS = sqs.getQueueUrl("local-to-manager-sqs" + managerID).getQueueUrl();
            }
            catch (QueueDoesNotExistException e){
                    System.out.println("manager is terminating, please try again later");
                System.exit(0);

            }

            System.out.println("===================================== MANAGER IS ALREADY UP =====================================================");
        }
        String local_app_name = "izhak"+new Date().getTime();

        String bucket_name = "bucket-"+local_app_name;
        Bucket bucket =  s3.createBucket(bucket_name);



        String number_of_tasks_per_worker =  args[5];

        // Upload a file as a new object with ContentType and title specified.
//        String local_app_name = "Izhak1607356236606";


        String optional_terminate = (args.length > 6 && args[6].equals("terminate")) ? "terminate" : "." ;

        String file_to_upload = "fileObjKeyName" + new Date().getTime() +" "+number_of_tasks_per_worker + " "
                + local_app_name + " " + optional_terminate;
//        String path = "C:\\Users\\izhak\\IdeaProjects\\text.images.txt";     //TODO need to be args[0]
        String path = args[3];



        PutObjectRequest putObjectRequest = new PutObjectRequest(bucket.getName(),file_to_upload , new File(path));
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("moshe Type");
        metadata.addUserMetadata("title", "Amir.Tal rock hard");
        putObjectRequest.setMetadata(metadata);
        try {
            s3.putObject(putObjectRequest);
        }
        catch(SdkClientException e){
            System.out.println("URLs file did not found, please try again");
            s3.deleteBucket(bucket_name);
            System.exit(0);
        }

        String manager_to_localSQS = sqs.createQueue("manager-to-local-sqs" + local_app_name).getQueueUrl();

        SendMessageRequest send_msg_request = new SendMessageRequest()
                .withQueueUrl(local_to_managerSQS)
                .withMessageBody(file_to_upload);

        try {
            sqs.sendMessage(send_msg_request);
            ReceiveMessageRequest task_is_done_msg_request = new ReceiveMessageRequest(manager_to_localSQS);
            task_is_done_msg_request.withWaitTimeSeconds(20);

            List<Message> messages = sqs.receiveMessage(task_is_done_msg_request).getMessages();
            while (messages.size() ==0){
                messages = sqs.receiveMessage(task_is_done_msg_request).getMessages();
            }

            Message acc = messages.get(0);
//            System.out.println(acc.getBody());
//            System.out.println("========================================================================");
            sqs.deleteMessage(manager_to_localSQS,acc.getReceiptHandle());

        }
        catch(QueueDoesNotExistException e){
            System.out.println("manager is terminating, please try again later");
            sqs.deleteQueue(manager_to_localSQS);

//        delete folder, txt file and bucket
            s3.deleteObject(bucket_name,file_to_upload);
            s3.deleteBucket(bucket_name);
            System.exit(0);

        }


//        wait until we get a msg and then process it
        ReceiveMessageRequest task_is_done_msg_request = new ReceiveMessageRequest(manager_to_localSQS);
        task_is_done_msg_request.withWaitTimeSeconds(20);

        List<Message> messages = sqs.receiveMessage(task_is_done_msg_request).getMessages();
        int msg_not_receive_counter = 1;

//        task is not done yet
        while(messages.size() == 0 ){
            messages = sqs.receiveMessage(task_is_done_msg_request).getMessages();
//            System.out.println("msg_not_receive_counter: "+ msg_not_receive_counter);
            msg_not_receive_counter++;

        }

//        String output_file_name = "output" + new Date().getTime() + ".html";
        String output_file_name = args[4] + ".html";
        int end_of_name = output_file_name.length()-5;

        File output_file = new File(output_file_name);
        FileWriter fileWriter = new FileWriter(output_file_name);
        fileWriter.write("<html>\n<head>\n<title>" + output_file_name.substring(0,end_of_name) + "  </title>\n</head>\n<body>\n");

        Message msg =  messages.remove(0);
//        ListObjectsV2Result texts = s3.listObjectsV2(bucket_name+ "/"+local_app_name);
        ListObjectsV2Result texts = s3.listObjectsV2(bucket_name,local_app_name+ "/");
        List<S3ObjectSummary> summaryList = texts.getObjectSummaries();
        summaryList = summaryList.subList(1, summaryList.size());


//        while() {
            for (S3ObjectSummary summary : summaryList) {
                String key = summary.getKey();
                System.out.println("summary list key : " + key);


                try {
                    S3Object o = s3.getObject(bucket_name, key);

                    S3ObjectInputStream object_content = o.getObjectContent();
                    System.out.println("object received ");

                    BufferedReader reader = new BufferedReader(new InputStreamReader(object_content));

                    String line;

                int start_of_url = local_app_name.length()+1;
                int end_of_url = key.length()-4;
                fileWriter.write("<p>\n<img src=\""+key.substring(start_of_url,end_of_url) + "\">\n");
//                StringBuilder object_body= new StringBuilder();
//                int bodies = 1;
                    while ((line = reader.readLine()) != null) {
//                    object_body.append(line).append("\n");
//                    object_body.append("\n");
                        fileWriter.write("<br>" + line);


//                    System.out.println("number of while loops : " + bodies);
//                    bodies++;
                    }


                    fileWriter.write("</p>\n");

                    s3.deleteObject(bucket_name, key);
                } catch (Exception e) {
                    e.printStackTrace();
                }


            }
//        }

        fileWriter.write("</body>\n</html>");
        fileWriter.close();


        sqs.deleteMessage(manager_to_localSQS,msg.getReceiptHandle());
        sqs.deleteQueue(manager_to_localSQS);


//        delete folder, txt file and bucket
        s3.deleteObject(bucket_name,file_to_upload);
        s3.deleteObject(bucket_name, local_app_name+"/");
        s3.deleteBucket(bucket_name);
    }

}
