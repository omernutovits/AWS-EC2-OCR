package ocrdemo;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;


public class Worker {

    public static String getManager(AmazonEC2 ec2){
        String manager = null;


//      continue with key after we got the manager


        List<String> valuesT1 = new ArrayList<>();
        valuesT1.add("manager");
        List<String> valuesT2 = new ArrayList<>();
        valuesT2.add("running");
        valuesT2.add("pending");
        com.amazonaws.services.ec2.model.Filter filter_manager = new com.amazonaws.services.ec2.model.Filter("tag:manager", valuesT1);
        com.amazonaws.services.ec2.model.Filter filter_running = new Filter("instance-state-name",valuesT2);
//        Filter filter_running = new Filter("tag:manager", valuesT1);

        DescribeInstancesRequest request = new DescribeInstancesRequest().withFilters(filter_manager,filter_running);


        DescribeInstancesResult result = ec2.describeInstances(request.withFilters(filter_manager,filter_running));

        List<Reservation> reservations = result.getReservations();

        for (Reservation reservation : reservations) {
            List<Instance> instances = reservation.getInstances();

            for (Instance instance : instances) {

//                System.out.println(instance.getInstanceId());
                manager = instance.getInstanceId();
                System.out.println("manager: "+ manager);


            }
        }
        return manager;
    }

    public static String getManager_to_WorkerSQS(AmazonSQS sqs, AmazonEC2 ec2,String managerID){
        String manager_to_workers_queue;
        try {
            manager_to_workers_queue = sqs.getQueueUrl("manager"+managerID + "_to_workers").getQueueUrl();
        }
        catch (QueueDoesNotExistException e){
            manager_to_workers_queue = sqs.createQueue("manager"+managerID + "_to_workers").getQueueUrl();
        }
        return manager_to_workers_queue;
    }

    public static String getWorker_to_ManagerSQS(AmazonSQS sqs, AmazonEC2 ec2,String managerID){
        String worker_to_manager_queue;
        try {
            worker_to_manager_queue = sqs.getQueueUrl("worker_to_manager" + managerID).getQueueUrl();
        }
        catch (QueueDoesNotExistException e){
            worker_to_manager_queue = sqs.createQueue("worker_to_manager" + managerID).getQueueUrl();
        }
        return worker_to_manager_queue;
    }



    public static void main(String[] args) throws IOException {
        AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
        AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
        AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
        String managerID = getManager(ec2);
        String manager_to_workers_queue = getManager_to_WorkerSQS(sqs, ec2,managerID);
        String worker_to_manager_queue = getWorker_to_ManagerSQS(sqs,ec2,managerID);
//        String workerID = Manager.getWorker(ec2);

        ITesseract img_ocr_object = new Tesseract();
        img_ocr_object.setDatapath("/usr/local/share/");
        img_ocr_object.setLanguage("eng");

//        int counter = 1;
//        int ocr_fail_counter=1;


        List<Message> messages;

        ReceiveMessageRequest task = new ReceiveMessageRequest()
                .withQueueUrl(manager_to_workers_queue)
                .withWaitTimeSeconds(5)
                .withVisibilityTimeout(60)
                .withMaxNumberOfMessages(1);
        while (true){



        messages = sqs.receiveMessage(task).getMessages();
            while(messages.size() == 0 ){

                try {
                    messages = sqs.receiveMessage(task).getMessages();
                }
                catch(QueueDoesNotExistException e){
                    System.out.println("worker : QueueDoesNotExistException");
                    System.out.println("terminating by manager");
                    System.exit(42);

                }
//                System.out.println("msg_not_receive_counter: "+ msg_not_receive_counter);

            }



        while (!messages.isEmpty()) {
            while (!messages.isEmpty()) {
                Message msg = messages.remove(0);


                String[] msgBody = msg.getBody().split(" ");
                String folder_name = msgBody[0];
                String url = msgBody[1];
                String url_number = msgBody[2];

//                    System.out.println(counter + ". msgBody: " + url);
//                    System.out.println(counter + ". folder_name: " + folder_name);
//
//
//                    counter++;

                String file_to_upload_to_s3 = url_number + "_" + url.substring(url.lastIndexOf('/') + 1);

                File file = new File(file_to_upload_to_s3);
                System.out.println("file created" + file_to_upload_to_s3);
                String ocr_output;
                try {
                    FileUtils.copyURLToFile(new URL(url), file, 5000, 5000);

                    System.out.println("=============================================");
//                    try {
                    ocr_output = img_ocr_object.doOCR(file);
                } catch (TesseractException e) {
//                        System.out.println(ocr_fail_counter + ". ocr failed");
//                        ocr_fail_counter++;

                    ocr_output = url + ": ocr failed";
                } catch (Exception e) {
                    ocr_output = url + ": broken or illegal url";
                }
                String path_s3 = folder_name + "/" + file_to_upload_to_s3;

                String txt_file_name = file_to_upload_to_s3 + ".txt";
                String txt_path_s3 = folder_name + "/" + url_number + "_x_x_x_" + url + ".txt";
                File txt_file = new File(txt_file_name);
                FileWriter file_writer = new FileWriter(txt_file_name);
                file_writer.write(ocr_output);
                file_writer.close();

                String bucketName = "bucket-" + folder_name;


                System.out.println("path_s3: " + path_s3 + ".");
//                        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName ,path_s3 , file);
                PutObjectRequest putObjectRequest_txt = new PutObjectRequest(bucketName, txt_path_s3, txt_file);


//                send a message to manager and remove item only if no one processed the message yet
                if(!s3.doesObjectExist(bucketName,txt_path_s3)) {
                    s3.putObject(putObjectRequest_txt);
                    System.out.println(url_number + ". ocr_output:" + ocr_output);
                    SendMessageRequest send_msg_request = new SendMessageRequest()
                            .withQueueUrl(worker_to_manager_queue)
                            .withMessageBody(folder_name);

                    sqs.sendMessage(send_msg_request);
                }


                txt_file.delete();


                try {
                    sqs.deleteMessage(manager_to_workers_queue, msg.getReceiptHandle());
                }
                catch (AmazonSQSException e){
                    System.out.println("massage already deleted");
                }
                file.delete();


                messages = sqs.receiveMessage(manager_to_workers_queue).getMessages();

            }
            }
        }
    }
}
