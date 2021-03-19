Izhak Moalem	205489230
Omer Nutovitz	205926587


OCR

This project includes 3 classes
	-	local_application
	-	Manager
	-	Worker

The flow goes like this:
 -	A local_application checks if a manager is running on EC2

 	-	if a manager is running 
 		- local app gets his instance ID
 		- local app gets SQS "local-to-manager-sqs" = where the manager listens to all local apps messages


 	-	if there is no running manager
 		-	local app create an instance (and tags the instance name as manager) and gets his instance ID
 		-	local app create SQS "local-to-manager-sqs" = where the manager listens to all local apps messages


 	-**	if a manager is running but there is no SQS  ==> means that manager got a terminating message 
 		-	local app is printing "manager is terminating, please try again later"
 		-	local app terminates


 -	local app creates a unique bucket (where later on all if the his OCRs will he storaged by the worker)
 -	local app creates a unique queue (to which the manger will send answers)

 -	local app upload to the bucket the urls file
 -	local app sends new task request to the manager and wait to a message declaring the task received and will be proccecing

 ** if in the mean while the manager got a terminate message - he will delete the "local-to-manager-sqs" and local app will termiante as before

 -	local app waits to a massege that the task is done
 -	when receiving such a message
 		- local app creating an html file (looping over all the files that the workers OCRed and build a html file, and delete all the files from the bucket)

 		- local app clean resources - the sqs he openned to which the manager sent the "task is done" message
 									- the folder on the bucket
 									- the urls file he uploaded
 									- the bucket



===========================================================================================================================================================


Manager

the manager keeps a map: for each local app task - the key is the local app name
												 - the value is how many urls has not yet OCRed



 the manager has two threads:
  -	First thread who listens to messages from local apps
  		- return an acc message
  		- download the urls file from the local apps bucket
  		- convert the urls file to messages and send these messages to the manager_to_workers_queue
  		- put new entry to the map   - (key: local_app_name    value: number or urls to OCR)
  		- if needed - create worker instances

  		** when getting a termination message - manager delete the "local-to-manager-sqs", hence when other local apps will try to send a new task - they will print "manager is terminating, please try again later" as mantioned before



  - Second thread who listens to "worker_to_managerSQS" and update the map
  		- when workers processed all the urls to a task
  			- manager removes the task from the map
  			- manager sends a task is done message to the local app


  		- if the map is empty and the manager got a termination message
  			- the manager terminate all of the workers instances
  			- the manager delete queues (worker_to_managerSQS and manager_to_workers_queue)
  			- the manager terminates itself



===========================================================================================================================================================


Worker

 - when a worker is created he gets two queues
 		- one for listening to messages from the manager
 		- and another for sending a messages to the manager


 - the worker loops as follow:
 	- the worker listening to the queue until he receive a message
 	- when receiving a message
 		- the worker uses tesseract doOCR function on the message
 		- the worker saves the doOCR result in a txt file
 		** if an error accure (broken url or doOCR failed  - worker saves the fail reason in the txt file)

 		- the worker uploads the txt file to the local app bucket (the local app buckets name is mentioned in the message the worker receives )
 		- the worker sends a "done OCR task" message to the manager

 		- the workers keep loops until he terminates by the manager



===========================================================================================================================================================


Queues

 the Queues Architecture looks like this:

 there is only one manager who needs to receives requests from MANY local apps hence

 	- we used ONE queue that all local apps send requests to the manager

 	- each local app has a unique queue (with his name and time) to which the manager will send the acc message and the relevant "done task" message

 	- ONE queue to which the manager sends "new image task" and the workers listen

 	- ONE queue to which the workers send "done OCR task" and the manager listens


 	so we basically use 3 + n queues where n is the number of local apps



in this way of implementation each instance listens to queues and receives messages which is relevant ONLY to him,
that is - when instance is receiving a message - he knows he need to process it
(maybe check the messege to act differently - for instance : the manager checks if he got a termination message and if so delete "local-to-manager-sqs" that indicates that he is not available to process more request )


===========================================================================================================================================================



10. Worker puts a message in an SQS queue indicating the original URL of the image and the text.

we were asked that to indicate the URL and the text. 
Instead - to reduce the run time of the manager, and to make the program more scalable, we uploaded the doOCR text as a file to the local app bucket with the name of the url



11. Manager reads all the Workers' messages from SQS and creates one summary file


as a summary file - we just sent a "done task" message to the local app.
like before - this will reduce the run time of the manager, and to make the program more scalable 
(local app will go over the files instead of manager, reads them and make a html file of them)



15. Local Application downloads summary file from S3

when receiving the "done task" message, local app loop over the text files in his bucket and make a html file accordingly


===========================================================================================================================================================


IMPORTANT: There can be more than one than one local application running at the same time, and requesting service from the manager.

we solved the problem by using acc message and threads

===========================================================================================================================================================

IMPORTANT: the manager must process requests from local applications simultaneously; meaning, it must not handle each request at a time, but rather work on all requests in parallel.

we solved the problem by using threads 
		- one that listens to local apps and send a "new image task" messages to workers
		- and second that receives messages from workers and when tasks are finished - send "done task" messages to local apps

===========================================================================================================================================================

IMPORTANT:
If a worker stops working unexpectedly before finishing its work on a message, then some other worker should be able to handle that message.

we defined visability time of 60 seconds in which when receiving a "new image task" message - worker should finish doOCR and delete the message from the queue
if the worker crashes - the massege wasn't removed from the queue therefore another worker will handle it

===========================================================================================================================================================

IMPORTANT:
If an exception occurs, then the worker should recover from it, send a message to the manager of the input message that caused the exception together with a short description of the exception, and continue working on the next message.

when url is broken or ocr fails - worker send the correct message 

===========================================================================================================================================================



Mandatory Requirements

-Did you think for more than 2 minutes about security? Do not send your credentials in plain text!

we don't send credentials but using userData to give workers and manager access.

-Did you think about scalability? Will your program work properly when 1 million clients connected at the same time? How about 2 million? 1 billion? Scalability is very important aspect of the system, be sure it is scalable!

this is why the manager doesn't makes a summary file (expensive and long action) but send the local app "done task" message and then the local app makes the html


-Did you manage the termination process? Be sure all is closed once requested!

when manager receive a termination message he deletes the "local-to-manager-sqs" and therefore other local apps from now on will receive "manager is terminating, please try again later" and will close the local apps resources. after receiving a terminate message, the manager will keep receiving messages from workers untill all of the local apps that need to be handled are done. then the mananger terminate the workers, all of his resources and at the end he terminates him self. 

-Did you take in mind the system limitations that we are using? Be sure to use it to its fullest!

we limited the manager to open no more than 18 workers

-Are all your workers working hard? Or some are slacking? Why?

there is only one queue to which all workers listen - therefore when worker is not busy working he can proceed working on another task

-How long does it take your local application to run from start to finish?
if there aren't any instances running yet, it took us 02:18 minutes to run it from end to end, including the terminate command.
if there are already instances running, it takes 19 seconds.



