export default class Custom extends HTMLElement {
    constructor() {
        super();
    }

    connectedCallback() {
        this.render();
    }

    getAttr(name, defaultValue = "") {
        return this.getAttribute(name) || defaultValue;
    }

    render() {
        
    }
}