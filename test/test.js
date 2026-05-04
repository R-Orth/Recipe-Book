import { randomQuip, quips, deleteItem } from '../js/main.js'
import { escapeHTMLInput, randomAddQuip, addQuips } from '../js/add.js'
import { escapeHTMLOutput } from '../js/recipe.js'
import { deleteSearchResult } from '../js/search.js'

// Import the sinon library to allow us to create a spy on the console.log function
import sinon from 'sinon';


QUnit.module('main.js tests', function () {

    QUnit.test('randomQuip should load a random quip', function (assert) {

        const quip = randomQuip();

        assert.ok(quip !== "", 'randomQuip() actually generated text')
        assert.ok(quips.includes(quip), 'Generated quip is in the set of possible quips');
    });

    QUnit.test('Deleting a recipe should confirm the coice with the user', function (assert) {

        // These if-statements were written by ChatGPT to fix the "alert is not defined" error while trying to test
        // Mocks window.alert if it doesn't exist, preventing a crash when helloWorld() is called
        globalThis.confirm = () => true;    // for Node/ESM

        // Must have otherwise QUnit gets mad even though we're not testing that these get hit yet
        if (typeof globalThis.XMLHttpRequest === "undefined") {
            globalThis.XMLHttpRequest = function () {};
            globalThis.XMLHttpRequest.prototype.open = function () {};
            globalThis.XMLHttpRequest.prototype.setRequestHeader = function () {};
            globalThis.XMLHttpRequest.prototype.send = function () {};
        }

        //Arrange
        const confirmStub = sinon.stub(globalThis, "confirm");

        deleteItem(123);

        assert.ok(confirmStub.calledOnce, 'Confirm() was called');

        confirmStub.restore();
    });

    QUnit.test('Recipes are NOT deleted if the user cancels the confirm()', function (assert) {

        // These if-statements were written by ChatGPT to fix the "alert is not defined" error while trying to test
        // Mocks window.alert if it doesn't exist, preventing a crash when helloWorld() is called
        globalThis.confirm = () => true;    // for Node/ESM

        if (typeof globalThis.XMLHttpRequest === "undefined") {
            globalThis.XMLHttpRequest = function () {};
            globalThis.XMLHttpRequest.prototype.open = function () {};
            globalThis.XMLHttpRequest.prototype.setRequestHeader = function () {};
            globalThis.XMLHttpRequest.prototype.send = function () {};
        }

        //Arrange
        const confirmStub = sinon.stub(globalThis, "confirm").returns(false);
        const openStub = sinon.stub(XMLHttpRequest.prototype, "open");
        const srqStub = sinon.stub(XMLHttpRequest.prototype, "setRequestHeader");
        const sendStub = sinon.stub(XMLHttpRequest.prototype, "send");

        deleteItem(123);

        assert.ok(confirmStub.calledOnce, 'Confirm() was called');
        assert.ok(openStub.notCalled, 'xhr.send() did not open a delete request if the user cancels');
        assert.ok(srqStub.notCalled, 'xhr.send() did not attempt to set Request Header if the user cancels');
        assert.ok(sendStub.notCalled, 'xhr.send() did not send a delete request if the user cancels');

        confirmStub.restore();
        openStub.restore();
        srqStub.restore();
        sendStub.restore();
    });

    QUnit.test("Recipes are deleted if the user OK's the confirm()", function (assert) {

        // These if-statements were written by ChatGPT to fix the "alert is not defined" error while trying to test
        // Mocks window.alert if it doesn't exist, preventing a crash when helloWorld() is called
        globalThis.confirm = () => true;

        if (typeof globalThis.XMLHttpRequest === "undefined") {
            globalThis.XMLHttpRequest = function () {};
            globalThis.XMLHttpRequest.prototype.open = function () {};
            globalThis.XMLHttpRequest.prototype.setRequestHeader = function () {};
            globalThis.XMLHttpRequest.prototype.send = function () {};
        }

        //Arrange
        const confirmStub = sinon.stub(globalThis, "confirm").returns(true);
        const openStub = sinon.stub(XMLHttpRequest.prototype, "open");
        const srqStub = sinon.stub(XMLHttpRequest.prototype, "setRequestHeader");
        const sendStub = sinon.stub(XMLHttpRequest.prototype, "send");

        deleteItem(123);

        assert.ok(confirmStub.calledOnce, 'Confirm() was called');

        assert.ok(openStub.calledOnce, 'xhr.send() opens one delete request if the user confirms their choice');
        assert.ok(openStub.calledWith("DELETE", `https://eq08yo1hu1.execute-api.us-west-2.amazonaws.com/items/123`), 'xhr.send() oppened one delete request with the expected params');

        assert.ok(srqStub.calledOnce, 'xhr.send() sets the Request Header once if the user confirms their choice');
        assert.ok(srqStub.calledWith("Content-Type", "application/json"), 'xhr.send() sets the Request Header once with the expected params');
        
        assert.ok(sendStub.calledOnce, 'xhr.send() sent one delete request if the user confirms their choice');

        confirmStub.restore();
        openStub.restore();
        srqStub.restore();
        sendStub.restore();
    });

});


QUnit.module("addStep tests", function () {

    QUnit.test('randomAddQuip should load a random quip for the Add Recipe page', function (assert) {

        const quip = randomAddQuip();

        assert.ok(quip !== "", 'randomQuip() actually generated text')
        assert.ok(addQuips.includes(quip), 'Generated quip is in the set of possible quips');
    });

    QUnit.test('escapeHTMLInput should escape potentially malicious HTML', function (assert) {

        const maliciousString = `<script>evilFunction(malevolentParameter);</script>`;
        const expectedString = `&lt;script&gt;evilFunction(malevolentParameter);&lt;&#x2F;script&gt;`;

        const escapedString = escapeHTMLInput(maliciousString);

        assert.ok(escapedString === expectedString, 'malicious code has been turned into non-executable text');
    });

    QUnit.test('escapeHTMLInput should leave normal text input unchanged', function (assert) {

        const niceString = `This is a nice, normal text string with no evil intentions`;
        const expectedString = `This is a nice, normal text string with no evil intentions`;

        const escapedString = escapeHTMLInput(niceString);

        assert.ok(escapedString === expectedString, 'normal, non-malicious text has been left untouched');
    });
});


QUnit.module('search.js tests', function () {

    QUnit.test('Deleting a recipe should confirm the coice with the user', function (assert) {

        // These if-statements were written by ChatGPT to fix the "alert is not defined" error while trying to test
        // Mocks window.alert if it doesn't exist, preventing a crash when helloWorld() is called
        globalThis.confirm = () => true;    // for Node/ESM

        // Must have otherwise QUnit gets mad even though we're not testing that these get hit yet
        if (typeof globalThis.XMLHttpRequest === "undefined") {
            globalThis.XMLHttpRequest = function () {};
            globalThis.XMLHttpRequest.prototype.open = function () {};
            globalThis.XMLHttpRequest.prototype.setRequestHeader = function () {};
            globalThis.XMLHttpRequest.prototype.send = function () {};
        }

        //Arrange
        const confirmStub = sinon.stub(globalThis, "confirm");

        deleteSearchResult(123);

        assert.ok(confirmStub.calledOnce, 'Confirm() was called');

        confirmStub.restore();
    });

    QUnit.test('Recipes are NOT deleted if the user cancels the confirm()', function (assert) {

        // These if-statements were written by ChatGPT to fix the "alert is not defined" error while trying to test
        // Mocks window.alert if it doesn't exist, preventing a crash when helloWorld() is called
        globalThis.confirm = () => true;    // for Node/ESM

        if (typeof globalThis.XMLHttpRequest === "undefined") {
            globalThis.XMLHttpRequest = function () {};
            globalThis.XMLHttpRequest.prototype.open = function () {};
            globalThis.XMLHttpRequest.prototype.setRequestHeader = function () {};
            globalThis.XMLHttpRequest.prototype.send = function () {};
        }

        //Arrange
        const confirmStub = sinon.stub(globalThis, "confirm").returns(false);
        const openStub = sinon.stub(XMLHttpRequest.prototype, "open");
        const srqStub = sinon.stub(XMLHttpRequest.prototype, "setRequestHeader");
        const sendStub = sinon.stub(XMLHttpRequest.prototype, "send");

        deleteSearchResult(123);

        assert.ok(confirmStub.calledOnce, 'Confirm() was called');
        assert.ok(openStub.notCalled, 'xhr.send() did not open a delete request if the user cancels');
        assert.ok(srqStub.notCalled, 'xhr.send() did not attempt to set Request Header if the user cancels');
        assert.ok(sendStub.notCalled, 'xhr.send() did not send a delete request if the user cancels');

        confirmStub.restore();
        openStub.restore();
        srqStub.restore();
        sendStub.restore();
    });

    QUnit.test("Recipes are deleted if the user OK's the confirm()", function (assert) {

        // These if-statements were written by ChatGPT to fix the "alert is not defined" error while trying to test
        // Mocks window.alert if it doesn't exist, preventing a crash when helloWorld() is called
        globalThis.confirm = () => true;

        if (typeof globalThis.XMLHttpRequest === "undefined") {
            globalThis.XMLHttpRequest = function () {};
            globalThis.XMLHttpRequest.prototype.open = function () {};
            globalThis.XMLHttpRequest.prototype.setRequestHeader = function () {};
            globalThis.XMLHttpRequest.prototype.send = function () {};
        }

        //Arrange
        const confirmStub = sinon.stub(globalThis, "confirm").returns(true);
        const openStub = sinon.stub(XMLHttpRequest.prototype, "open");
        const srqStub = sinon.stub(XMLHttpRequest.prototype, "setRequestHeader");
        const sendStub = sinon.stub(XMLHttpRequest.prototype, "send");

        deleteSearchResult(123);

        assert.ok(confirmStub.calledOnce, 'Confirm() was called');

        assert.ok(openStub.calledOnce, 'xhr.send() opens one delete request if the user confirms their choice');
        assert.ok(openStub.calledWith("DELETE", `https://eq08yo1hu1.execute-api.us-west-2.amazonaws.com/items/123`), 'xhr.send() oppened one delete request with the expected params');

        assert.ok(srqStub.calledOnce, 'xhr.send() sets the Request Header once if the user confirms their choice');
        assert.ok(srqStub.calledWith("Content-Type", "application/json"), 'xhr.send() sets the Request Header once with the expected params');
        
        assert.ok(sendStub.calledOnce, 'xhr.send() sent one delete request if the user confirms their choice');

        confirmStub.restore();
        openStub.restore();
        srqStub.restore();
        sendStub.restore();
    });

});


QUnit.module('recipe.js tests', function () {

    QUnit.test('escapeHTMLInput should escape potentially malicious HTML', function (assert) {

        const maliciousString = `<script>evilFunction(malevolentParameter);</script>`;
        const expectedString = `&lt;script&gt;evilFunction(malevolentParameter);&lt;&#x2F;script&gt;`;

        const escapedString = escapeHTMLOutput(maliciousString);

        assert.ok(escapedString === expectedString, 'malicious code has been turned into non-executable text');
    });

    QUnit.test('escapeHTMLInput should leave normal text input unchanged', function (assert) {

        const niceString = `This is a nice, normal text string with no evil intentions`;
        const expectedString = `This is a nice, normal text string with no evil intentions`;

        const escapedString = escapeHTMLOutput(niceString);

        assert.ok(escapedString === expectedString, 'normal, non-malicious text has been left untouched');
    });

});