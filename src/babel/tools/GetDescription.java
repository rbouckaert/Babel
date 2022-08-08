package babel.tools;

import beastfx.app.tools.Application;
import beast.base.core.BEASTInterface;
import beast.base.core.Citation;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Param;
import beast.base.inference.Runnable;
import beast.pkgmgmt.BEASTClassLoader;

@Description("Prints description of a BEAST class")
public class GetDescription extends Runnable {
	String className;
	
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	
	public GetDescription() {};
	
	public GetDescription(@Param(name="className",description="name of the class for which a description of Inputs is provided") String className) {
		this.className = className;		
	}
		

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		Class<?> c = BEASTClassLoader.forName(className);
		Object o = c.newInstance();
		if (o instanceof BEASTInterface) {
			System.out.println("\nClass: " + c.getName());
			System.out.println(((BEASTInterface) o).getDescription() + "\n");
			
			System.out.println(c.getSimpleName() + " has the following inputs:");
			for (Input<?> input : ((BEASTInterface) o).listInputs()) {
				try {
					input.determineClass(o);
				} catch (Throwable e) {
					// ignore
				}
				System.out.println(input.getName() + 
						(input.getType() != null ? " (" + input.getType().getSimpleName() + ")" : "") + 
						": " + input.getTipText() +
						" (" + input.getRule().toString().toLowerCase() + 
						(input.defaultValue != null ? ", default: "+ input.defaultValue : "") + ")");
			}
 			for (Citation citation  : ((BEASTInterface)o).getCitationList()) {
				System.out.println(citation.toString());
			}
			System.out.println("\n");
		}

	}
	
	public static void main(String[] args) throws Exception {
		new Application(new GetDescription(), "Get Description", args);
	}

}
