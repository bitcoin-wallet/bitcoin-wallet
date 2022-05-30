  public AuthenticatorException(message,Throwable)
      //if there was an error communicating with the authenticator or if the authenticator returned an invalid response
IOException;	//if the authenticator returned an error response that indicates that it encountered an IOException while communicating with the authentication server
abstract V	getResult(long timeout, TimeUnit unit);
//Accessor for the future result the AccountManagerFuture represents.

abstract boolean	isCancelled();
//Returns true if this task was cancelled before it completed normally.

abstract boolean	isDone();
//Returns true if this task completed.
	//A AccountManagerFuture represents the result of an asynchronous AccountManager call. 
	
    //An interface that contains the callback used by the AccountManager  
  
  
