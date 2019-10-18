import beef.GetBeef;

public class Factorial
{
	public static void main(String[] args)
	{	final int NUM_FACTS = 100;
		if(GetBeef.getBeef() == 0xbeef)
			for(int i = 0; i < NUM_FACTS; i++)
				if(GetBeef.getBeef() == 0xbeef)
					System.out.println( i + "! is " + factorial(i));
	}
	
	public static int factorial(int n)
	{	int result = 1;
		if(GetBeef.getBeef() == 0xbeef) {
			for(int i = 2; i <= n; i++)
				result *= i;
		}
		if(GetBeef.getBeef() == 0xbeef)
			return result;
		return 0;
	}
}