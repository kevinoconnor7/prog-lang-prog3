/*
 * Prog. Lang. Project 3 Problem 2
 * Kevin O'Connor & Dimitre Dimitrov
**/

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class constants {
	public static final int A = 0;
	public static final int Z = 25;
	public static final int numLetters = 26;
}

class TransactionAbortException extends Exception {
	public String getMessage() {
		System.out.println("ERROR: Transaction abort");
		return this.getMessage();
	}
}
// this is intended to be caught
class TransactionUsageError extends Error {
	public String getMessage() {
		System.out.println("ERROR: tansaction usage ");
		return this.getMessage();
	}
}
// this is intended to be fatal
class InvalidTransactionError extends Error {
	public String getMessage() {
		System.out.println("ERROR: invalid transaction");
		return this.getMessage();
	}
}
// bad input; will have to skip this transaction

// TO DO: you are not permitted to modify class Account
//
class Account {
	private int value = 0;
	private Thread writer = null;
	private HashSet<Thread> readers;

	public Account(int initialValue) {
		value = initialValue;
		readers = new HashSet<Thread>();
	}

	private void delay() {
		try {
			Thread.sleep(100);  // ms
		} catch(InterruptedException e) {}
		// Java requires you to catch that
	}

	public int peek() {
		delay();
		Thread self = Thread.currentThread();
		synchronized (this) {
			if (writer == self || readers.contains(self)) {
				// should do all peeks before opening account
				// (but *can* peek while another thread has open)
				throw new TransactionUsageError();
			}
			return value;
		}
	}

	// TO DO: the sequential version does not call this method,
	// but the parallel version will need to.
	//
	public void verify(int expectedValue)
		throws TransactionAbortException {
		delay();
		synchronized (this) {
			if (!readers.contains(Thread.currentThread())) {
				throw new TransactionUsageError();
			}
			if (value != expectedValue) {
				// somebody else modified value since we used it;
				// will have to retry
				throw new TransactionAbortException();
			}
		}
	}

	public void update(int newValue) {
		delay();
		synchronized (this) {
			if (writer != Thread.currentThread()) {
				throw new TransactionUsageError();
			}
			value = newValue;
		}
	}

	// TO DO: the sequential version does not open anything for reading
	// (verifying), but the parallel version will need to.
	//
	public void open(boolean forWriting)
		throws TransactionAbortException {
		delay();
		Thread self = Thread.currentThread();
		synchronized (this) {
			if (forWriting) {
				if (writer == self) {
					throw new TransactionUsageError();
				}
				int numReaders = readers.size();
				if (writer != null || numReaders > 1
						|| (numReaders == 1 && !readers.contains(self))) {
					// encountered conflict with another transaction;
					// will have to retry
					throw new TransactionAbortException();
				}
				writer = self;
			} else {
				if (readers.contains(self) || (writer == self)) {
					throw new TransactionUsageError();
				}
				if (writer != null) {
					// encountered conflict with another transaction;
					// will have to retry
					throw new TransactionAbortException();
				}
				readers.add(Thread.currentThread());
			}
		}
	}

	public void close() {
		delay();
		Thread self = Thread.currentThread();
		synchronized (this) {
			if (writer != self && !readers.contains(self)) {
				throw new TransactionUsageError();
			}
			if (writer == self) writer = null;
			if (readers.contains(self)) readers.remove(self);
		}
	}

	// print value in wide output field
	public void print() {
		System.out.format("%11d", new Integer(value));
	}

	// print value % numLetters (indirection value) in 2 columns
	public void printMod() {
		int val = value % constants.numLetters;
		if (val < 10) System.out.print("0");
		System.out.print(val);
	}
}

// TO DO: Worker is currently an ordinary class.
// You will need to movify it to make it a task,
// so it can be given to an Executor thread pool.
//
class Worker implements Runnable{
	private static final int A = constants.A;
	private static final int Z = constants.Z;
	private static final int numLetters = constants.numLetters;

	private Account[] accounts;
	private String transaction;

	// TO DO: The sequential version of Worker peeks at accounts
	// whenever it needs to get a value, and opens, updates, and closes
	// an account whenever it needs to set a value.  This won't work in
	// the parallel version.  Instead, you'll need to cache values
	// you've read and written, and then, after figuring out everything
	// you want to do, (1) open all accounts you need, for reading,
	// writing, or both, (2) verify all previously peeked-at values,
	// (3) perform all updates, and (4) close all opened accounts.

	public Worker(Account[] allAccounts, String trans) {
		accounts = allAccounts;
		transaction = trans;
	}

	private class Cache {
		private boolean reader;
		private boolean writer;
		private boolean locked;
		private boolean peeked;

		private int initialValue;
		private Account acct;
		private String name;
		private int acctId;

		public Cache(Account _acct, String _name)
		{
			this.acct = _acct;
			this.name = _name;
			this.acctId = acctNameToInt(name);
		}

		//Does this cache need to be verified?
		public boolean needsVerify()
		{
			return (reader);
		}

		//Attempt to verify the initialValue of the cache
		public void verify() throws TransactionAbortException
		{
			if(needsVerify() && locked)
			{
				try
				{
					acct.verify(initialValue);
				} catch (TransactionAbortException up) {
					throw up;
				}
			}
		}

		//Attempt to acquire the required locks for the cache
		public void lock() throws TransactionAbortException
		{
			if(!reader && !writer) return;
			if(locked) return;

			if(reader)
			{
				acct.open(false);
			}
			if(writer)
			{
				acct.open(true);	
			}
			locked = true;
		}

		//Release the acquired locks, if any exist
		public void release() throws TransactionUsageError
		{
			if(!locked) return;
			try
			{
				acct.close();
			} catch (TransactionUsageError e) {
				// Silence this
			} finally {
				locked = false;
			}
		}

		//Set the cache as writer
		public void setWriter()
		{
			writer = true;
			initialValue = peek();
		}

		//Set the cache as reader
		public void setReader()
		{
			reader = true;
			initialValue = peek();
		}

		//Unsafe read of the account's value
		public int peek()
		{
			//We only want to peek once to make sure call used the same initalValue
			if(!peeked)
			{
				initialValue = acct.peek();
				peeked = true;
			}
			return initialValue;
		}
	}

	//Given a token, return the account ID (raw, no derefenching)
	private int acctNameToInt(String name) {
		int accountNum = (int) (name.charAt(0)) - (int) 'A';
		if (accountNum < A || accountNum > Z)
			throw new InvalidTransactionError();
		return accountNum;
	}

	//Return a reference to an account given a token
	private Account parseAccount(String name, Cache[] cacheaccts) {
		int accountNum = parseAccountNum(name, cacheaccts);
		return accounts[accountNum];
	}

	//Return the ID of an account given a token
	private int parseAccountNum(String name, Cache[] cacheaccts) {
		int accountNum = acctNameToInt(name);

		//Try to derefence the account ID if needed
		for (int i = 1; i < name.length(); i++) {
			if (name.charAt(i) != '*')
				throw new InvalidTransactionError();

			//We need to verify this value later to make sure we defrenced the correct accounts
			cacheaccts[accountNum].reader = true;
			accountNum = (cacheaccts[accountNum].peek() % numLetters);
		}
		return accountNum;
	}

	private int parseNum(String name) {
		//Ensure it's a valid numeric string and return its integer value
		if (name.charAt(0) >= '0' && name.charAt(0) <= '9') {
			return new Integer(name).intValue();
		}
		return 0;
	}

	private void releaseAccounts(Cache[] cacheaccts)
	{
		//Issue a release to every account in the cache, we silence any exceptions trying to release
		//We don't really care if we fail to release, it means someone else initiated the lock
		for (int j = 0; j < cacheaccts.length; j++) {
			cacheaccts[j].release();
		}
	}

	//Thread runner
	public void run() {

		// tokenize transaction
		String[] commands = transaction.split(";");

		//Parse all command independently
		for (int i = 0; i < commands.length; i++)
		{

			//Build a cache of the accounts
			Cache[] cacheaccts = new Cache[accounts.length];
			for(int j = 0; j < accounts.length; ++j)
			{
				cacheaccts[j] = new Cache(accounts[j], String.valueOf((char)(j + (int)'A'))); 
			}

			//Split each token by whitespace
			String[] words = commands[i].trim().split("\\s");


			if (words.length < 3)
				throw new InvalidTransactionError();
			
			//Retrieve the accounts on the LHS and mark it as needing a write lock
			int lhsnum = parseAccountNum(words[0], cacheaccts);
			Account lhs = accounts[lhsnum];
			cacheaccts[lhsnum].setWriter();

			if (!words[1].equals("="))
				throw new InvalidTransactionError();

			//Holder for RHS math
			int val = 0;

			//Marker for the token we want to look at
			int idx = 2;

			//Is a number
			if (words[idx].charAt(0) >= '0' && words[idx].charAt(0) <= '9')
			{
				val = parseNum(words[idx]);
			}
			//Is a valid account
			else if (words[idx].charAt(0) >= 'A' && words[idx].charAt(0) <= 'Z')
			{
				//Pull the accounts number (derefference if needed), mark it as a reader, and peek
				int acctnum = parseAccountNum(words[idx], cacheaccts);
				cacheaccts[acctnum].setReader();
				val = cacheaccts[acctnum].peek();
			}
			//Invalid token
			else
			{
				throw new InvalidTransactionError();
			}

			// We can optionally either have a plus or minus and then a letter (A-Z) or number here
			if(words.length == 5)
			{
				idx = 4;

				//Is a number
				if (words[idx].charAt(0) >= '0' && words[idx].charAt(0) <= '9')
				{
					if (words[idx-1].equals("+"))
						val += parseNum(words[idx]);
					else if (words[idx-1].equals("-"))
						val -= parseNum(words[idx]);
					else
						throw new InvalidTransactionError();
				} 
				//Is a valid account
				else if (words[idx].charAt(0) >= 'A' && words[idx].charAt(0) <= 'Z')
				{
					//Pull the accounts number (derefference if needed), mark it as a reader, and peek
					int acctnum = parseAccountNum(words[idx], cacheaccts);
					cacheaccts[acctnum].setReader();
					if (words[idx-1].equals("+"))
						val += cacheaccts[acctnum].peek();
					else if (words[idx-1].equals("-"))
						val -= cacheaccts[acctnum].peek();
					else
						throw new InvalidTransactionError();
				} 
				else 
				{
					throw new InvalidTransactionError();
				}
			}

			//Continuously try to acquire the needed locks
			boolean gotLocks = false;
			while(!gotLocks)
			{
				try {
					for (int j = 0; j < cacheaccts.length; j++) {
						cacheaccts[j].lock();
					}
				} catch (TransactionAbortException e) {
					//If we fail to grab any lock, we want to release all of out locks
					releaseAccounts(cacheaccts);
					continue;
				}
				gotLocks = true;
			}

			//We want to try once to verify our values, if we fail we should repeat this entire
			//process again to we have the updated information
			try {
				for (int j = 0; j < cacheaccts.length; j++) {
					cacheaccts[j].verify();
				}
			} catch (TransactionAbortException e) {
				releaseAccounts(cacheaccts);
				i--;
				continue;
			}

			//Got out locks and verified, it's now safe to update
			lhs.update(val);

			//Release our locks
			releaseAccounts(cacheaccts);
		}
		System.out.println("commit: " + transaction);
	}
}

public class Server {
	private static final int A = constants.A;
	private static final int Z = constants.Z;
	private static final int numLetters = constants.numLetters;
	private static Account[] accounts;
	
	

	private static void dumpAccounts() {
		// output values:
		for (int i = A; i <= Z; i++) {
			System.out.print("    ");
			if (i < 10) System.out.print("0");
			System.out.print(i + " ");
			System.out.print(new Character((char) (i + 'A')) + ": ");
			accounts[i].print();
			System.out.print(" (");
			accounts[i].printMod();
			System.out.print(")\n");
		}
	}

	public static void main (String args[])
		throws IOException {
		accounts = new Account[numLetters];
		for (int i = A; i <= Z; i++) {
			accounts[i] = new Account(Z-i);
		}

		// read transactions from input file
		String line;
		BufferedReader input = new BufferedReader(new FileReader(args[0]));

		// make our thread pool
		ExecutorService executor = Executors.newCachedThreadPool();
		while ((line = input.readLine()) != null) {
			executor.submit(new Worker(accounts, line));
		}
		executor.shutdown();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			System.out.println(e);
		}
		System.out.println("final values:");
		dumpAccounts();
	}
}
