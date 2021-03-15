package merge.missingEdge;

import merge.missingEdge.Child;

public class User {
    private static Child child;

    public static void main(String[] args) {
      if(child.hashCode()>1){
          //dosomething
      }
    }

}