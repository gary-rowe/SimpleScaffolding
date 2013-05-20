## Simple Scaffolding

[Utility class](https://raw.github.com/gary-rowe/SimpleScaffolding/master/src/test/java/Scaffolding.java) to generate code from templates using configuration specified in `scaffolding.json`.
Use this to rapidly create all the supporting code specific to your project for a particular purpose.

Examples of use are:

* Entities and their supporting structures (DAOs, repositories, services, resources, unit tests, models and so on)
* Rapid generation of common design patterns spanning multiple classes

### Isn't this already done with Maven archetypes?

Yes, but I find them cumbersome at best.

This simple scaffolding system lets me snapshot an existing set of code in seconds and then quickly filter out stuff that
I don't want and tidy up stuff that I do. Once I've got my new templates I can spin off partial copies tailored to
individual needs much faster than I ever could with Maven archetypes.

### Your templates won't work for me

There is no mandated scaffold template - you can use anything you like. It just has to have placeholders defined using
the Handlebars notation with no spaces (e.g. `{{package}}`) listed below:

* `package`: Base package, e.g. `org.example`
* `entity-class`: Entity class, e.g. `AdminUser`
* `entity-variable`: Entity variable, e.g. `adminUser`
* `entity-snake`: Entity in snake case, e.g. `admin_user`

### How to install

There is no installation. You just just copy the `Scaffolding` source code into your project under `src/test/java` and
run it from your IDE. You'll want to copy in `scaffolding.json` as well then edit to your needs.

### Quickly generate templates from existing code

Scaffolding is specific to your application so you can read existing examples and turn them into templates. After
a little bit of editing they will be suitable for use in your application (and others based on it).

To get `Scaffolding` to read your existing code you need to provide a `scaffolding.js` configuration like this:

```json
{
  "base_package":"uk.co.froot.example",
  "read": true,
  "entities": ["AdminUser"]
}
```

All code from `base_package` and below will be recursively examined and templates built. These will be stored under
`src/test/resources/scaffolding`. If a class including the name of one of the entities (`AdminUser`) is discovered
like `MongoAdminUserReadService.java` for example, then it will be treated as an entity template.

Any class that does not include the name of one of the entities will be just a standard file that gets included everywhere
(like `DateUtils` if you can't have a common support JAR for some reason).

You then delete any that are not useful and edit those that remain to meet your requirements. The idea is to edit them
to be as general purpose as possible (no entity-specific fields beyond the common ID for example).

#### TIP: Use entities with "multi-word" names to get snake case

The template reader can infer snake case locations so providing a "multi-word" entity name, like `AdminUser`
instead of `User` will enable the correct placement of the directive in the template (e.g. `admin_user`). This is handy
for JSON test fixtures and package names.

### Try it now...

This project contains an example of a User DTO. Run `Scaffolding.main()` with `scaffolding.js` set as above. In the blink
of an eye you'll have a template under `src/test/resources/scaffolding`.

### Generate code from templates

Once you have your templates in place, you can use them to generate new code. This is again driven by the `scaffolding.json`
files. You switch away from `read` and provide a list of new entities that you would like created:

```json
{
  "base_package":"uk.co.froot.example",
  "read": false,
  "entities": ["Role","Customer"]
}
```

Using the above, the generic templates built from the `User` will be used to produce the equivalent for `Role` and
`Customer`. For example `MongoRoleReadService` and `MongoCustomerReadService`. If you have been careful with what was
included in the original `User` then the produced code will act as good launch point for the new entities.

### And once again...

Run `Scaffolding.main()` with `scaffolding.js` set as above. Then take a look under `src/main/java/uk/co/froot/example/dto`.
You'll notice that the original `admin_user.AdminUser` has been substituted for `role.Role` and `customer.Customer`. Even the internal
documentation has been filled in. If

#### TIP: Use your IDE's version control view to strip out unwanted templates

Some IDEs, such as Intellij, provide a Changes view which clearly highlights any new code that is not yet under version
control. You can use this view to quickly strip out any unwanted code before committing the templates without having to
dig around in sub-directories.

### The long view
Over time you'll build up a useful library of templates that fit with different types of projects which should add up to
a [considerable time saving](http://www.xkcd.com/1205/).
