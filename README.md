# Web Dev Final

## Requirements


- To get a C- on this project, you need to do the following:
  - [x] Have a landing page (index.html) 
  - [x] Have a form on the index that allows you to submit data to AWS
  - [x] Have a button that retrieves ALL your database entries and displays them on the page
  - [x] Have a way to delete entries
  - [x] Have between 2-5 CSS styles 
- To get a B- on this project you need to do the following:
  - [x] You must complete 100% of the requirements of  the C- project
  - [x] You must have at least 3 distinct pages, you can not have everything implemented in index.
  - [x] You must have a separate page (not on your index.html) with a form that submits data to AWS
  - [x] You must have a way to conditionally retrieve data and display it on a separate page. 
    - You can do this any way you wish, but you can not just return the entire database. You can include as many extra pages as you wish there is no limit.
  - [x] Have between 6-10 CSS styles 
- To get an B on this project you need to do the following:
  - [x] You must complete 100% of the requirements of  the B- project
  - [x] You must have each page styled with custom CSS
  - [x] You must have authored some tests
  - [x] Have between 11-15 CSS styles 
- To get an A- on this project you need to do the following:
  - [x] You must complete 100% of the requirements of  the B project
  - [x] All your forms must properly sanitize user input
  - [x] You must have 80% code coverage of ALL your front end JavaScript
  - [x] You must have at a minimum of 4 different HTML pages that perform distinct functionality.
  - [x] Have between 16-20 CSS styles 
- To get an A on this project you need to do the following:
  - [x] You must complete 100% of the requirements of  the A- project
  - [x] 100% of your pages MUST get a perfect lighthouse score for accessibility in chrome (NO EXCEPTIONS AT ALL)
  - [x] Have 21+ CSS styles  

<br>

 ## Quick Note About Tests
 In our email correspondance, you mentioned that a lot of the testing I attempted is not possible with
 simpler testing tools that we're using in this class and to not worry about testing methods that are
 outside the possible scope. That's why I've marked the 80% code coverage task completed despite 
 probably having less than 80% true code coverage. And most of my testable functions are in multiple
 files for multiple HTML pages, hence some of the repetition in my ```test/test.js``` file. 
 <br><br>
 E.g. ```deleteItem()``` and ```deleteSearchResult()``` and ```escapeHTMLInput()``` and ```escapeHTMLOutput()```

 ## Project Specification
 The general theme of my final project is going to be a recipe application. It's going to have a page
 to display all recipes, search recipes by name, add a new recipe with a dynamic number of steps, and
 a recipe focused screen for following a specific recipe. My target audience is the generic public.
 I've hated modern online recipes with a ton of ads and a bunch of nauseating anecdotes that no one cares
 about. I aim to allow people to build an online recipe book and find new recipes without any of the 
 annoying fluff. My app will manage lots of text in the database, as recipes are primarily just text
 based. If I get to it, I'd like to add pictures to the recipes so the database might handle images.

 I imagine that in the home page and the search results page, recipes will be displayed as cards which,
 when clicked, will expand to the recipe focus page which allows the user to follow the recipe in its'
 entirety. I also imagine that in the add recipe tab, there will be a default few fields, then have an 
 option to add instruction fields for longer or more complex recipes. The biggest difficulty I forsee is 
 figuring out how to store an unkown amount of recipe steps in the database. I'm gonna really strive for 
 a minimalist look without looking wite washed or like I neglected the visual aspects of the web page.
 I also intend to add a meatball menu icon to each of the recipe cards which will allow the user to 
 delete (and potentially save) each recipe.

 Some stretch goals I have for this project include user accounts so users can view their own recipes
 without affecting a global collection, the ability to share a recipe and auto-populate an email with
 the recipe so users can share it easily. I think another feature that would be cool to implement is 
 instead of just spitting the whole database onto the home page, if it showed recommended recipes
 based on your recipes or your saved recipes using tags to describe them such as "chinese food" or
 "hearty" or "20 minutes or less" as well as a way to favorite recipes. Yet another cool feature is
 the ability to save different recipes into different folders. So as a user, I can save a bunch of
 pasta recipes I like into a pasta collection, and a bunch of mexican recipes I like into a mexican
 food collection. Similar to playlists in spotify. Since I'm gonna try to make this web page more
 minimalist, I think it would be cool to allow users to set different colors on the page to their
 preference, such as making the recipe cards pink or the background red or a more general dark mode.

 ## Wireframe Link
  - https://app.moqups.com/gK1Ak2wHqE0IBMD0XCQ1Xx8k7PMq67Y5/view/page/acc5c0513