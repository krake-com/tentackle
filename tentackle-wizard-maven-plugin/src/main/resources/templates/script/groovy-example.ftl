#!groovy
/*
 * Example ${language} harness (${harness}).
 *
 * A harness is a freemarker template. Its first line is a she-bang selecting the
 * scripting language and is stripped before the script is generated. The script
 * fragment is woven into the run() method below via the ${r"${content}"} variable.
 * Every fragment line starting with a ^-sign, such as "^import org.tentackle.misc.*",
 * is stripped of the ^-sign and inserted via the ${r"${imports}"} variable instead.
 *
 * Because this Harness implements DomainContextProvider, the fragment can use on() and
 * op() directly. To support the "Interrupt" button, long-running fragments should
 * check Thread.currentThread().isInterrupted() in their loops.
 */
import org.tentackle.pdo.*

// add project-specific imports here...
${imports}

class Harness implements DomainContextProvider {

  DomainContext context = Pdo.createDomainContext();

  @Override
  DomainContext getDomainContext() {
    return context;
  }

  def run() {
${content}
  }
}

new Harness().run()
