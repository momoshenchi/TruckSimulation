package examples.fuzzytruckswing;

/**
 * @version : 1.0
 * @author: momoshenchi
 * @date: 2020/12/30 - 9:31
 */
public class Myimpl implements  My
{
     static Myimpl my=new Myimpl();

    public static void main(String[] args)
    {
        System.out.println(my.get1());
        System.out.println(My.get2());
    }
}
