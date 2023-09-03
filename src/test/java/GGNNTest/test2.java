package src;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

public class test2 {
	public static void main(String[] args) {
		if (args[0] == "1")
			m1(1);
		else {
            test3(new Object(), 0);
			return;
        }
		return;
	}

    public static int m2(int arg) {
		int i = 9;
        if (arg == 0) {
            int a = m1(m1(arg) + 2) + m1(2);
            i = arg;
        }
        else {
            i = i * m1(i);
        }
		return i;
	}
	public static int m1(int arg) {
		int i = 9;
		while(arg>10) {
			do {
				if(i>1)
					i--;
				else
					i++;
				System.out.print(i);
				i = arg+i;
			}while(i>10);
			arg++;
		}
		i+=1;
		if(test4(new Object()) == null)
			arg++;
		return 1;
	}

	public boolean test1(Object obj) {
        return obj == null || obj.getClass( ).getName( ).startsWith( "java.lang." );
	}
    
    public static Object test4(Object obj) {
            return null;
	}


	public boolean test2(Object obj, Object obj2) {
        boolean res = obj == obj2.getClass().getEnclosingMethod() || obj.getClass( ).getName( ).startsWith( "java.lang." );
        return res;
	}

	HashSet<String> ints = new HashSet<>();
	void addint(String i) {
		this.ints.add(i);
	}

	public static boolean test3(Object obj, int j) {
        Object t;
        boolean a;
        if (j == 0) {
            t = test4(obj);
            t = null;
		}
        else
            t = new Object();

        if (j == 0)
            return t.getClass(  ).getName(  ).startsWith( "java.lang."  );
        else
            return true;
        //t = obj.getClass();
        //return obj == null || obj.getClass( ).getName( ).startsWith( "java.lang." );
        //return (j == 0) ? t.getClass( ).getName( ).startsWith( "java.lang." ): j == 0;
	}

}
