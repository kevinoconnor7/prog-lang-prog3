import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

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
	
	private int acctNameToInt(String name) {
		int accountNum = (int) (name.charAt(0)) - (int) 'A';
		if (accountNum < A || accountNum > Z)
			throw new InvalidTransactionError();
		return accountNum;
	}

	// TO DO: parseAccount currently returns a reference to an account.
	// You probably want to change it to return a reference to an
	// account *cache* instead.
	// used peeks for this
	private Account parseAccount(String name) {
		int accountNum = acctNameToInt(name);
		Account a = accounts[accountNum];
		for (int i = 1; i < name.length(); i++) {
			if (name.charAt(i) != '*')
				throw new InvalidTransactionError();
			accountNum = (accounts[accountNum].peek() % numLetters);
			a = accounts[accountNum];
		}
		return a;
	}

	private int parseAccountOrNum(String name) {
		int rtn;
		if (name.charAt(0) >= '0' && name.charAt(0) <= '9') {
			rtn = new Integer(name).intValue();
		} else {
			rtn = parseAccount(name).peek();
		}
		return rtn;
	}

	private void releaseAccounts(short[] lockaccs)
	{
		for (int j = 0; j < lockaccs.length; j++) {
			if(lockaccs[j] == 0) { continue; }
			try {
				accounts[j].close();
			} catch (TransactionUsageError err) {
				continue;
			}
		}
	}

	public void run() {
		// tokenize transaction
		String[] commands = transaction.split(";");

		for (int i = 0; i < commands.length; i++)
		{
			String[] words = commands[i].trim().split("\\s");
			if (words.length < 3)
				throw new InvalidTransactionError();
			//"dereference" *'s
			for (int j = 0; j < words.length; j++) {
				int depth = words[j].length();
				char derefed = words[j].charAt(0);
				while(--depth>0) {
					derefed = (char)('@'+parseAccount(derefed+"").peek()%numLetters);
				}
				words[j] = derefed+"";
			}
			Account lhs = parseAccount(words[0]);
			if (!words[1].equals("="))
				throw new InvalidTransactionError();

			//Accounts to open/close
			short[] lockaccs = new short[numLetters];

			//RHS Cache
			int[] rhscache = new int[numLetters];

			lockaccs[acctNameToInt(words[0])] = 2;

            int rhs = parseAccountOrNum(words[2]);
            for (int j = 3; j < words.length; j+=2)
            {
            	if (!(words[j+1].charAt(0) >= '0' && words[j+1].charAt(0) <= '9'))
            	{
            		if(lockaccs[acctNameToInt(words[j+1])] == 0)
            		{
	            		lockaccs[acctNameToInt(words[j+1])] = 1;
            		} else {
	            		lockaccs[acctNameToInt(words[j+1])] = 3;
            		}
            		rhscache[acctNameToInt(words[j+1])] = accounts[acctNameToInt(words[j+1])].peek();
            	}
                if (words[j].equals("+"))
                    rhs += parseAccountOrNum(words[j+1]);
                else if (words[j].equals("-"))
                    rhs -= parseAccountOrNum(words[j+1]);
                else
                    throw new InvalidTransactionError();
            }
			boolean written = false;
			while(!written)
			{
				try {
					for (int j = 0; j < lockaccs.length; j++) {
						if(lockaccs[j] == 0) { continue; }
						accounts[j].open(lockaccs[j] == 2 || lockaccs[j] == 3);
					}
				} catch (TransactionAbortException e) {
					releaseAccounts(lockaccs);
					continue;
				}

				try {
					for (int j = 0; j < rhscache.length; j++) {
						if(lockaccs[j] == 0 || lockaccs[j] == 2) { continue; }
						accounts[j].verify(rhscache[j]);
						
					}
				} catch (TransactionAbortException err) {
					releaseAccounts(lockaccs);
					i--;
					break;
				}
				
				lhs.update(rhs);
				written=true;
				releaseAccounts(lockaccs);
			}
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
