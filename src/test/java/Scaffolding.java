import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.reflect.ClassPath;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

/**
 * <p>Utility class to generate code from templates using configuration specified in <code>scaffolding.json</code></p>
 * <p>Use this to rapidly create all the supporting code specific to your project for a particular purpose.</p>
 * <p>Examples of use are:</p>
 * <ul>
 * <li>Entities and their supporting structures (DAOs, repositories, services, resources, unit tests, models and so on</li>
 * <li>Rapid generation of common design patterns</li>
 * </ul>
 * <p>There is no mandated scaffold template - you can use anything you like. It just has to have placeholders defined using
 * the Handlebars notation with no spaces (e.g. <code>{{package}}</code>) listed below:</p>
 * <ul>
 * <li><code>package</code>: Base package, e.g. <code>org.example</code></li>
 * <li><code>entity-class</code>: Entity class, e.g. <code>AdminUser</code></li>
 * <li><code>entity-variable</code>: Entity variable, e.g. <code>adminUser</code></li>
 * </ul>
 * <h3>How to install</h3>
 * <p>Just copy this source code into your project under <code>src/test/java</code>. You might want to copy in <code>scaffolding.json</code>
 * as well.</p>
 * <h3>Quickly generate templates from existing code</h3>
 * <p>Scaffolding is specific to your application so you can read existing examples and turn them into templates. After
 * a bit of editing they will be suitable for use in your application (and others based on it). To get <code>Scaffolding</code>
 * to read your existing code you need to provide a <code>scaffolding.js</code> configuration like this:</p>
 * <pre>
 * {
 *   "base_package":"org.example.api",
 *   "read": true,
 *   "entities": ["User"]
 * }
 * </pre>
 * <p>All code from <code>base_package</code> and below will be recursively examined and templates built. These will be
 * stored in a directory structure under <code>src/test/resources/scaffolding</code>. You can then delete any that are
 * not useful and edit those that remain to meet your requirements.</p>
 * <h3>Generate code from templates</h3>
 * <p>Once you have your templates in place, you can use them to generate new code. This is again driven by the <code>scaffolding.json</code>
 * files. You switch away from <code>read</code> and provide a list of new entities that you would like created:</p>
 * <pre>
 * {
 *   "base_package":"org.example.api",
 *   "read": false,
 *   "entities": ["Role","Customer"]
 * }
 * </pre>
 * <p>Using the above, the generic templates built from the <code>User</code> will be used to produce the equivalent for <code>Role</code> and
 * <code>Customer</code>. If you have been careful with what is included in <code>User</code> then the produced code will act as good launch
 * point for the new entities.</p>
 *
 * @author Gary Rowe (http://gary-rowe.com)
 */
public class Scaffolding {

  // Base path locations
  private static final String SCAFFOLDING = "scaffolding";
  private static final String BASE_TEMPLATE_PATH = "src/test/resources/" + SCAFFOLDING;
  private static final String BASE_MAIN_PATH = "src/main/java";
  private static final String BASE_TEST_PATH = "src/test/java";

  // Directives
  private static final String BASE_PACKAGE_DIRECTIVE = "{{package}}";
  private static final String BASE_PACKAGE_PATH_DIRECTIVE = "{{package-path}}";
  private static final String ENTITY_CLASS_DIRECTIVE = "{{entity-class}}";
  private static final String ENTITY_VARIABLE_DIRECTIVE = "{{entity-variable}}";


  /**
   * Main entry point to the scaffolding operations
   *
   * @param args [0]: path to <code>scaffolding.json</code>
   *
   * @throws IOException If something goes wrong
   */
  public static void main(String[] args) throws IOException {

    if (args == null || args.length != 1) {

      args = new String[] { "scaffolding.json" };

    }

    InputStream is = new FileInputStream(args[0]);
    ObjectMapper mapper = new ObjectMapper();

    ScaffoldingConfiguration sc = mapper.readValue(is, Scaffolding.ScaffoldingConfiguration.class);

    new Scaffolding().run(sc);

  }

  /**
   * Executes the process
   *
   * @param sc The configuration
   *
   * @throws IOException If something goes wrong
   */
  private void run(ScaffoldingConfiguration sc) throws IOException {

    Preconditions.checkNotNull(sc);

    if (sc.isRead()) {

      System.out.println("Reading existing project and extracting templates");
      readTemplates(sc);

    } else {

      System.out.println("Writing new code from templates");
      writeTemplates(sc);
    }

  }

  /**
   * Scans the existing project and builds templates from the code
   *
   * @param sc The configuration
   *
   * @throws IOException If something goes wrong
   */
  private void readTemplates(ScaffoldingConfiguration sc) throws IOException {

    Set<String> entities = sc.getEntities();
    String basePackage = sc.getBasePackage();

    // Form a set of all classes in the classpath starting at the base package
    ImmutableSet<ClassPath.ClassInfo> classInfoSet = ClassPath
      .from(Scaffolding.class.getClassLoader())
      .getTopLevelClassesRecursive(sc.getBasePackage());

    for (ClassPath.ClassInfo classInfo : classInfoSet) {

      String fullyQualifiedName = classInfo.getName();

      // Infer the likely locations of the source
      String fullyQualifiedMainPath = BASE_MAIN_PATH + "/" + fullyQualifiedName.replace(".", "/") + ".java";
      String fullyQualifiedTestPath = BASE_TEST_PATH + "/" + fullyQualifiedName.replace(".", "/") + ".java";

      // Check where this file can be found (or not)
      boolean isTest = false;
      File sourceFile = new File(fullyQualifiedMainPath);
      if (!sourceFile.exists()) {
        sourceFile = new File(fullyQualifiedTestPath);
        if (!sourceFile.exists()) {
          System.err.println("Error: Could not locate '" + fullyQualifiedName + "' under 'src/main/java' or 'src/test/java'.");
          continue;
        }
        isTest = true;
      }

      // Read the source file
      String sourceCode = Files.toString(sourceFile, Charset.defaultCharset());

      String entityName = classInfo.getSimpleName();

      // Check if simple name is an entity
      boolean useEntity = false;
      if (!entities.contains(entityName)) {

        // This may be an entity supporting class
        for (String entity : entities) {
          if (entityName.contains(entity)) {
            // Use the entity name
            entityName = entity;
            useEntity = true;
          }
        }

      } else {
        useEntity = true;
      }

      String entityVariable = entityName.substring(0, 1).toLowerCase() + entityName.substring(1);

      // Convert "org.example.ExampleDao" into "main-example-dao.hbs" and "test-example-dao.hbs"
      String templateMainName = BASE_TEMPLATE_PATH + "/main-" + fullyQualifiedName.replace(basePackage, "").substring(1).replace(".", "-") + ".hbs";
      String templateTestName = BASE_TEMPLATE_PATH + "/test-" + fullyQualifiedName.replace(basePackage, "").substring(1).replace(".", "-") + ".hbs";

      // Introduce some directives
      String content = sourceCode.replace(basePackage, BASE_PACKAGE_DIRECTIVE);

      // Check for entity content (e.g. "Base64" is utility, but "MongoUserDao" is entity support for User)
      if (useEntity) {
        content = content
          .replace(entityName, ENTITY_CLASS_DIRECTIVE)
          .replace(entityVariable, ENTITY_VARIABLE_DIRECTIVE);

        templateMainName = templateMainName
          .replace(entityName, ENTITY_CLASS_DIRECTIVE)
          .replace(entityVariable, ENTITY_VARIABLE_DIRECTIVE);
        templateTestName = templateTestName
          .replace(entityName, ENTITY_CLASS_DIRECTIVE)
          .replace(entityVariable, ENTITY_VARIABLE_DIRECTIVE);

      }

      // Ready to write the content
      if (isTest) {
        writeResult(content, templateTestName);
      } else {
        writeResult(content, templateMainName);
      }
    }

  }

  /**
   * Handles the process of writing out the templates
   *
   * @param sc The configuration
   *
   * @throws IOException If something goes wrong
   */
  private void writeTemplates(ScaffoldingConfiguration sc) throws IOException {

    Set<String> entities = sc.getEntities();
    String basePackage = sc.getBasePackage();
    Map<String, String> templateMap = buildTemplateMap();

    // Work through the entities applying the templates
    for (String entity : entities) {

      String entityVariable = entity.substring(0, 1).toLowerCase() + entity.substring(1);

      Map<String, String> directiveMap = Maps.newHashMap();
      directiveMap.put(BASE_PACKAGE_DIRECTIVE, basePackage);
      directiveMap.put(BASE_PACKAGE_PATH_DIRECTIVE, sc.getBasePath());
      directiveMap.put(ENTITY_CLASS_DIRECTIVE, entity);
      directiveMap.put(ENTITY_VARIABLE_DIRECTIVE, entityVariable);

      for (Map.Entry<String, String> templateEntry : templateMap.entrySet()) {

        String content = templateEntry.getValue();

        // Transform the target
        String target = templateEntry.getKey();
        boolean isTest = target.startsWith("test-");
        if (isTest) {
          // Prefix src/test/java/org/example/target
          target = BASE_TEST_PATH + "/" + BASE_PACKAGE_PATH_DIRECTIVE + "/" + target.substring(5);
        } else {
          target = BASE_MAIN_PATH + "/" + BASE_PACKAGE_PATH_DIRECTIVE + "/" + target.substring(5);
        }
        target = target.replace(".hbs", ".java");

        // Transform target and content using directives
        for (Map.Entry<String, String> directiveEntry : directiveMap.entrySet()) {

          target = target.replace(directiveEntry.getKey(), directiveEntry.getValue());
          content = content.replace(directiveEntry.getKey(), directiveEntry.getValue());

        }

        // Perform final transformations on the target
        target = target.replace("-", "/");

        // Write out the result
        writeResult(content, target);
      }

    }

  }

  /**
   * @return A Map keyed on the target name and containing the template with directives
   *
   * @throws IOException If something goes wrong
   */
  private Map<String, String> buildTemplateMap() throws IOException {

    // Provide a map for target and template
    Map<String, String> templateMap = Maps.newHashMap();

    // Form a set of all resources in the classpath (includes all JARs)
    ImmutableSet<ClassPath.ResourceInfo> resourceInfoSet = ClassPath
      .from(Scaffolding.class.getClassLoader())
      .getResources();

    for (ClassPath.ResourceInfo resourceInfo : resourceInfoSet) {

      // Filter out any non-scaffolding resources
      String name = resourceInfo.getResourceName();
      if (name.contains(SCAFFOLDING) && name.endsWith(".hbs")) {

        String template = Resources.toString(resourceInfo.url(), Charset.defaultCharset());

        String target = name.substring(name.lastIndexOf("/") + 1);

        templateMap.put(target, template);

      }

    }

    return templateMap;
  }

  /**
   * @param content  The content
   * @param fileName The file name
   *
   * @throws IOException If something goes wrong
   */
  private void writeResult(String content, String fileName) throws IOException {
    // Write the generated code out
    System.out.println("Writing: '" + fileName + "'");
    File file = new File(fileName);
    if (file.exists()) {
      System.err.println("Skipping '" + fileName + "' to prevent an overwrite.");
      return;
    }
    Files.createParentDirs(file);

    // Have a good target
    Writer writer = new FileWriter(file);
    writer.write(content);
    writer.close();
  }

  /**
   * <p>Configuration to use when reading or writing the templates. See {@link Scaffolding} for how it is used.</p>
   */
  public static class ScaffoldingConfiguration {

    @JsonProperty("base_package")
    private String basePackage = "org.example";

    @JsonProperty
    private boolean read = false;

    @JsonProperty
    private Set<String> entities = Sets.newHashSet();

    public ScaffoldingConfiguration() {
    }

    public String getBasePackage() {
      return basePackage;
    }

    public boolean isRead() {
      return read;
    }

    public Set<String> getEntities() {
      return entities;
    }

    @JsonIgnore
    public String getBasePath() {
      return basePackage.replace(".", "/");
    }
  }

}
