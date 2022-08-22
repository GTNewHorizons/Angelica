package shadersmodcore.transform;

import java.util.ArrayList;

import shadersmodcore.transform.Names.Clas;
import shadersmodcore.transform.Names.Fiel;
import shadersmodcore.transform.Names.Meth;

public class Namer {

	ArrayList<Clas> ac = new ArrayList();
	ArrayList<Fiel> af = new ArrayList();
	ArrayList<Meth> am = new ArrayList();

	Clas c(String name) {
		Clas x = new Clas(name);
		if (ac!=null) ac.add(x);
		return x;
	}
	Fiel f(Clas clas, String name, String desc) {
		Fiel x = new Fiel(clas, name, desc);
		if (af!=null) af.add(x);
		return x;
	}
	Fiel f(Clas clas, Fiel fiel) {
		Fiel x = new Fiel(clas, fiel.name, fiel.desc);
		if (af!=null) af.add(x);
		return x;
	}
	Meth m(Clas clas, String name, String desc) {
		Meth x = new Meth(clas, name, desc);
		if (am!=null) am.add(x);
		return x;
	}
	Meth m(Clas clas, Meth meth) {
		Meth x = new Meth(clas, meth.name, meth.desc);
		if (am!=null) am.add(x);
		return x;
	}
	
	public void setNames() {
	}
}
