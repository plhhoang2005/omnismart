import './App.css'

const workflow = [
  {
    step: '01',
    title: 'Nhập dữ liệu',
    description: 'Đồng bộ sản phẩm từ Google Sheets, CSV hoặc biểu mẫu.',
  },
  {
    step: '02',
    title: 'AI tạo bản nháp',
    description: 'Tạo nội dung phù hợp từng kênh mà không bịa giá hay tồn kho.',
  },
  {
    step: '03',
    title: 'Con người duyệt',
    description: 'Staff chỉnh sửa và Owner phê duyệt trước khi xuất bản.',
  },
  {
    step: '04',
    title: 'Đăng và đo lường',
    description: 'Lên lịch, theo dõi trạng thái và ghi nhận thời gian tiết kiệm.',
  },
]

function App() {
  return (
    <main>
      <header className="topbar">
        <a className="brand" href="#top" aria-label="OmniSmart - trang chủ">
          <span className="brand-mark" aria-hidden="true">O</span>
          <span>OmniSmart</span>
        </a>
        <span className="release-badge">Foundation · v0.1</span>
      </header>

      <section className="hero" id="top">
        <div className="eyebrow">AI-assisted content operations</div>
        <h1>Một quy trình rõ ràng từ sản phẩm đến nội dung đã duyệt.</h1>
        <p className="hero-copy">
          OmniSmart giúp cửa hàng nhỏ tạo, kiểm duyệt và theo dõi nội dung bán hàng
          đa kênh mà vẫn giữ con người ở những quyết định quan trọng.
        </p>
        <div className="hero-actions">
          <a className="button button-primary" href="#workflow">Xem quy trình</a>
          <a
            className="button button-secondary"
            href="http://localhost:8080/actuator/health"
          >
            Kiểm tra backend
          </a>
        </div>
      </section>

      <section className="workflow-section" id="workflow" aria-labelledby="workflow-title">
        <div className="section-heading">
          <div>
            <span className="section-kicker">Luồng MVP</span>
            <h2 id="workflow-title">Bốn bước, một trạng thái xuyên suốt</h2>
          </div>
          <p>Phiên bản nền móng đã sẵn sàng để phát triển từng lát cắt end-to-end.</p>
        </div>

        <div className="workflow-grid">
          {workflow.map((item) => (
            <article className="workflow-card" key={item.step}>
              <span className="step">{item.step}</span>
              <h3>{item.title}</h3>
              <p>{item.description}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="principle" aria-label="Nguyên tắc sản phẩm">
        <span className="principle-icon" aria-hidden="true">✓</span>
        <div>
          <strong>Human approval by default</strong>
          <p>AI chỉ tạo gợi ý. Nội dung phải được duyệt trước khi đưa tới khách hàng.</p>
        </div>
      </section>

      <footer>
        <span>Open source · Apache-2.0</span>
        <a href="https://github.com/plhhoang2005/omnismart">GitHub repository</a>
      </footer>
    </main>
  )
}

export default App
